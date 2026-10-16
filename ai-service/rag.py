"""
RAG检索层——政策知识库检索

策略一览（search_policy 的 strategy 参数）：
  vector         纯向量检索（默认）
  bm25           纯关键词检索
  hybrid         向量 + BM25，RRF 融合
  rerank         向量召回 + 交叉编码精排
  hybrid_rerank  混合召回 + 精排（完整形态）
"""
import os
from pathlib import Path

import chromadb
import jieba
import requests
from dotenv import load_dotenv
from openai import OpenAI
from rank_bm25 import BM25Okapi

BASE_DIR=Path(__file__).resolve().parent
load_dotenv(BASE_DIR/".env")

CHROMA_DIR=BASE_DIR/"chroma_db"
COLLECTION_NAME="policy_knowledge"

#模型和向量维度必须与切分时的保持一致
EMBED_MODEL="text-embedding-v4"
EMBED_DIM=1024

DASHSCOPE_BASE_URL=os.getenv("DASHSCOPE_BASE_URL","")
DASHSCOPE_API_KEY=os.getenv("DASHSCOPE_API_KEY","")

os.environ.setdefault("NO_PROXY","127.0.0.1,localhost")
os.environ.setdefault("no_proxy","127.0.0.1,localhost")
#客户端全局只建这一次
embed_client1=OpenAI(api_key=DASHSCOPE_API_KEY,base_url=DASHSCOPE_BASE_URL)
#向量库句柄，第一次真用到的时候才打开
_collection=None
DEFAULT_STRATEGY=os.getenv("RAG_STRATEGY","vector")
def get_collection():
    """懒加载向量库"""
    global _collection
    if _collection is None:
        if not CHROMA_DIR.exists():
            raise RuntimeError(f"向量库不存在:{CHROMA_DIR}\n，请在ai-service目录执行：python ingest.py")
        client=chromadb.PersistentClient(path=str(CHROMA_DIR))
        _collection=client.get_collection(COLLECTION_NAME)
    return _collection

def vector_search(query: str,top_k: int = 3)->list[dict]:
    """
    检索政策知识库，返回最相关的top-k块

    返回格式：
    [{
    "text": "2. 期限的计算方式...",
    "doc_title": "七天无理由退货规则",
    "section": "2. 期限的计算方式"，
    “similarity”: 0.87},....]
    :param query:
    :param top_k:
    :return:
    """
    col=get_collection()

    #1:把用户问题转化为向量
    resp=embed_client1.embeddings.create(
        model=EMBED_MODEL,
        input=[query],
        dimensions=EMBED_DIM,
    )
    query_vector=resp.data[0].embedding

    #2:查库，chroma会按余弦距离返回最像的几条
    res=col.query(
        query_embeddings=[query_vector],
        n_results=top_k,
        include=["documents","metadatas","distances"]
    )

    #3:整理成上层好用的结构
    #chroma返回的是“二维数组”（因为支持一次查多个问题）
    #只查了一个问题，所以取【0】
    hits=[]
    docs=res["documents"][0]
    metas=res["metadatas"][0]
    dists=res["distances"][0]

    for text,meta,dist in zip(docs,metas,dists):
        hits.append({
            "text":text,
            "doc_title":meta.get("doc_title",""),
            "section":meta.get("section",""),
            #余弦距离是“越小越像”，转成“相似度”更符合直觉（越大越像）
            "similarity": round(1-dist,3)
        })
    return hits

# ============================================================
# 【新增1】关键词检索（BM25）—— 与向量互补：认精确词，不懂语义
# ============================================================
#补充领域词（带高词频）—— 不做这步，“退货运费”会被切成“退/货运费”，BM25效果大打折扣
DICT_WORDS=["退货","退款","换货","维修","运费","运费险","到账",
            "售后","工单","签收","吊牌","七天无理由","无理由退货",
            "质量问题","发票","会员","优惠券","积分","运费承担","退回"]

_bm25=None                       #BM25索引句柄，第一次用到才建
_bm25_docs:list[str]=[]
_bm25_metas:list[dict]=[]

def get_bm25():
    """从向量库里取出全部语料建 BM25 索引（38条，毫秒级，且与向量库天然一致）"""
    global _bm25,_bm25_docs,_bm25_metas
    if _bm25 is None:
        for w in DICT_WORDS:     #先补充领域词，再切分
            jieba.add_word(w,freq=5000000)
        data=get_collection().get(include=["documents","metadatas"])
        _bm25_docs=data["documents"]
        _bm25_metas=data["metadatas"]
        #中文必须先分词！不分词=整句话被当成一个词，BM25直接失效（而且不报错）
        tokenized=[list(jieba.cut(d)) for d in _bm25_docs]
        _bm25=BM25Okapi(tokenized)
    return _bm25

def bm25_search(query: str,top_k: int = 3)->list[dict]:
    """关键词检索，返回格式与 vector_search 对齐"""
    bm25=get_bm25()
    scores=bm25.get_scores(list(jieba.cut(query)))
    order=sorted(range(len(scores)),key=lambda i:-scores[i])[:top_k]
    return [{
        "text":_bm25_docs[i],
        "doc_title":_bm25_metas[i].get("doc_title",""),
        "section":_bm25_metas[i].get("section",""),
        #BM25分数量纲和余弦相似度不同，只能自己跟自己比，别跨策略比较
        "similarity":round(float(scores[i]),3),
    } for i in order]

# ============================================================
# 【新增2】RRF 融合 —— 把多路检索的“排名”合成一个排名
# ============================================================
def rrf_fuse(rank_lists: list[list[dict]],top_k: int,k: int = 60)->list[dict]:
    """Reciprocal Rank Fusion：只看名次、不看分数（两路分数量纲不同也能融合）
    rank_lists: [[向量结果...],[BM25结果...]]，各自已按相关度排好序
    k: 平滑常数，业界默认60，不用调
    """
    scores:dict[str,float]={}
    doc_of:dict[str,dict]={}
    for hits in rank_lists:
        for rank,h in enumerate(hits,start=1):
            key=h["text"]          #同一块的正文是唯一的，用它当身份标识
            doc_of[key]=h
            scores[key]=scores.get(key,0.0)+1.0/(k+rank)
    keys=sorted(scores,key=lambda x:-scores[x])[:top_k]
    return [doc_of[key] for key in keys]

def hybrid_search(query: str,top_k: int = 3)->list[dict]:
    """混合检索：向量 + BM25 各多召回一倍，RRF融合后取 top_k"""
    return rrf_fuse(
        [vector_search(query,top_k*2),bm25_search(query,top_k*2)],
        top_k,
    )

# ============================================================
# 【新增3】Rerank 精排 —— 交叉编码模型，对候选逐条细读
# ============================================================
#注意：走 DashScope 原生协议，不是 /compatible-mode 那个端点
RERANK_URL="https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
RERANK_MODEL="gte-rerank-v2"

def rerank_search(query: str,docs: list[dict],top_k: int = 3)->list[dict]:
    """对候选文档做交叉编码精排，取最相关的 top_k 条"""
    if not docs:
        return []
    resp=requests.post(
        RERANK_URL,
        headers={"Authorization":f"Bearer {DASHSCOPE_API_KEY}"},
        json={
            "model":RERANK_MODEL,
            "input":{"query":query,"documents":[d["text"] for d in docs]},
            "parameters":{"return_documents":False,"top_n":top_k},
        },
        timeout=15,
    )
    resp.raise_for_status()
    results=resp.json()["output"]["results"]
    #index是「传进去的数组下标」，不是名次——必须用它映射回原文档
    out=[]
    for r in results:
        h=dict(docs[r["index"]])
        #relevance_score很小（0.x），只能排序不能当阈值
        h["rerank_score"]=round(r["relevance_score"],4)
        out.append(h)
    return out

# ============================================================
# 对外入口：按 strategy 分发
# —— 上层（main.py 的工具、评估脚本）只认这一个函数
# ============================================================
def search_policy(query: str, top_k: int = 3, strategy: str | None = None) -> list[dict]:
    """
    检索政策知识库，返回最相关的 top_k 块。

    strategy 可选值：
      None / "vector"      → 纯向量检索（默认，最稳）
      "bm25"               → 纯关键词检索
      "hybrid"             → 向量 + BM25，RRF 融合
      "rerank"             → 向量召回 + 交叉编码精排
      "hybrid_rerank"      → 混合召回 + 精排（完整形态）

    返回格式：
    [{
      "text": "2. 期限的计算方式...",
      "doc_title": "七天无理由退货规则",
      "section": "2. 期限的计算方式",
      "similarity": 0.87
    }, ...]
    """
    # 不指定就用默认策略
    strategy = strategy or DEFAULT_STRATEGY

    if strategy == "vector":
        return vector_search(query, top_k)

    if strategy == "bm25":
        return bm25_search(query, top_k)

    if strategy == "hybrid":
        #降级：BM25 万一失败 → 退回纯向量
        try:
            return hybrid_search(query, top_k)
        except Exception as e:
            print(f"[降级] hybrid 失败 → 退到 vector：{e}")
            return vector_search(query, top_k)

    if strategy == "rerank":
        #降级：rerank 调不通 → 退回纯向量
        try:
            #召回放宽4倍，精排后取 top_k
            return rerank_search(query, vector_search(query, top_k * 4), top_k)
        except Exception as e:
            print(f"[降级] rerank 失败 → 退到 vector：{e}")
            return vector_search(query, top_k)

    if strategy == "hybrid_rerank":
        # 第 1 道：完整链路（效果最好）
        try:
            candidates = rrf_fuse(
                [vector_search(query, top_k * 4), bm25_search(query, top_k * 4)],
                top_k * 4,
            )
            return rerank_search(query, candidates, top_k)
        except Exception as e:
            print(f"[降级] hybrid_rerank 失败 → 退到 hybrid：{e}")

        # 第 2 道：融合结果（不依赖 rerank）
        try:
            return hybrid_search(query, top_k)
        except Exception as e:
            print(f"[降级] hybrid 失败 → 退到 vector：{e}")

        # 第 3 道：最后兜底（只依赖 embedding，和现网完全一致）
        return vector_search(query, top_k)

    raise ValueError(f"未知的检索策略：{strategy}")
if __name__=="__main__":
    #手动测试：默认走 vector；想测别的策略，改下面的 strategy 参数或设环境变量 RAG_STRATEGY
    for q in ["七天无理由怎么算", "退货运费谁承担", "发票怎么开"]:
        print(f"\n=== {q} ===")
        for h in search_policy(q):
            print(f"  [{h['similarity']}] 《{h['doc_title']}》/ {h['section']}")
