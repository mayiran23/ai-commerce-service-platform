"""
RAG检索层——政策知识库检索
"""
import os
from pathlib import Path

import chromadb
from dotenv import load_dotenv
from openai import OpenAI

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

def get_collection():
    """懒加载向量库"""
    global _collection
    if _collection is None:
        if not CHROMA_DIR.exists():
            raise RuntimeError(f"向量库不存在:{CHROMA_DIR}\n，请在ai-service目录执行：python ingest.py")
        client=chromadb.PersistentClient(path=str(CHROMA_DIR))
        _collection=client.get_collection(COLLECTION_NAME)
    return _collection

def search_policy(query: str,top_k: int = 3)->list[dict]:
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

if __name__=="__main__":
    for q in ["七天无理由怎么算", "退货运费谁承担", "发票怎么开"]:
        print(f"\n=== {q} ===")
        for h in search_policy(q):
            print(f"  [{h['similarity']}] 《{h['doc_title']}》/ {h['section']}")