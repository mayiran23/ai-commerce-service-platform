"""
RAG建索引脚本-把knowledge/里的政策文档切块、向量化、存进向量库
用法（在ai-service目录下执行）：
    .venv/Scripts/python.exe ingest.py
    #想重建
    .venv/Scripts/python.exe ingest.py --reset

设计说明：
1：按markdown的‘##’切块——一小节=一个检索单元
2：块文本里保留小结标题的文字（去掉##符号），因为标题本身语义很强
3：embedding走阿里云百炼text-embedding-v4（兼容OpenAI协议）
4：向量库是本地文件（chroma_db/）,不占端口、不用起服务
"""
import os
import re
import shutil
from pathlib import Path

import chromadb
from dotenv import load_dotenv
from openai import OpenAI

#1：配置信息
BASE_DIR=Path(__file__).resolve().parent   #ai-service/目录，锚定脚本位置，不受从哪运行的影响
#读取环境变量
load_dotenv(BASE_DIR/".env")
#表明政策文件和向量数据库所在目录
KNOWLEDGE_DIR=BASE_DIR/"knowledge"
CHROMA_DIR=BASE_DIR/"chroma_db"
#配置文本嵌入模型信息
DASHSCOPE_API_KEY=os.getenv("DASHSCOPE_API_KEY","")
DASHSCOPE_BASE_URL=os.getenv("DASHSCOPE_BASE_URL","")
EMBED_MODEL="text-embedding-v4"
EMBED_DIM=1024   #向量维度，text-embedding-v4默认为1024，检索时也应该使用1024
COLLECTION_NAME="policy_knowledge"
# ⚠️ text-embedding-v4 单次批量上限是 10 条（实测：传 20 条会被服务端拒绝，
#    报 "batch size is invalid, it should not be larger than 10"）。
#    用 8 留一点余量，避免个别条目 token 偏大时踩到总长度限制。
BATCH_SIZE=8
SKIP_FILES={"README.md","_template.md"}  #不是政策语料，跳过
#走本机时不要被系统代理拦掉
os.environ.setdefault("NO_PROXY","127.0.0.1,localhost")
os.environ.setdefault("no_proxy","127.0.0.1,localhost")
#没读到key就提前停，别等到调用的时侯才报错
if not DASHSCOPE_API_KEY:
    raise SystemExit("没读到DASHSCOPE_API_KEY")
embed_client=OpenAI(api_key=DASHSCOPE_API_KEY,base_url=DASHSCOPE_BASE_URL)

#解析并切分政策语料文档
#把文件头---包起来的front matter解析出来
def parse_front_matter(text):
    """解析YAML front matter,返回（字典，剩余正文）"""
    m = re.match(r"^---\s*\n(.*?)\n---\s*\n", text, re.S)
    if not m:
        return {}, text
    meta = {}
    for line in m.group(1).splitlines():
        if ":" in line:
            k, _, v = line.partition(":")
            meta[k.strip()] = v.strip()
    return meta, text[m.end():]
#切块函数
def split_by_section(text):
    """把一篇md按‘##’切块，返回【（小节题目，块文本）】
    ##之前的内容（front matter、#大标题）自动忽略
    """
    chunks=[]
    title=None
    lines=[]
    for line in text.split("\n"):             #逐行扫
        m=re.match(r"^##\s+(.+)",line)
        if m:             #遇到新小节
            if title is not None:             #先把上一块收起来
                chunks.append((title,"\n".join(lines).strip()))
            title=m.group(1).strip()            #去掉“##留标题
            lines=[title]                     #标题进块内容（语义强）
        elif title is not None:
            lines.append(line)                #小节内容
    if title is not None:
        chunks.append((title,"\n".join(lines).strip()))
    return chunks
#解析语料的函数
def load_sections():
    """读knowledge/下所有的政策文件->切成块
    返回【（文件名，小节标题，块文本），。。】
    """
    all_chunks=[]
    for f in sorted(KNOWLEDGE_DIR.glob("*.md")):
        if f.name in SKIP_FILES:
            continue
        text=f.read_text(encoding="utf-8")
        fm,body=parse_front_matter(text)
        for title,body in split_by_section(text):
            all_chunks.append((f.name,title,body,fm))
    return all_chunks
#向量化
def embed_texts(texts):
    """一批文本->一批向量（顺序一一对应）"""
    resp=embed_client.embeddings.create(
        model=EMBED_MODEL,
        input=texts,
        dimensions=EMBED_DIM,
    )
    items=sorted(resp.data,key=lambda d: d.index)  #按index排序，锁死顺序
    return [d.embedding for d in resp.data]

#存库＋主流程
def main(reset=False):
    if reset and CHROMA_DIR.exists():
        shutil.rmtree(CHROMA_DIR)          #--reset,先清空旧库

    chunks=load_sections()
    if not chunks:
        raise SystemExit("没切出任何块，检查语料标题格式（’##‘ 后面必须有空格）")
    print(f"共切出{len(chunks)}个块")

    client=chromadb.PersistentClient(path=str(CHROMA_DIR))
    collection=client.get_or_create_collection(COLLECTION_NAME,metadata={"hnsw:space":"cosine"})

    for i in range(0, len(chunks), BATCH_SIZE):
        batch=chunks[i:i+BATCH_SIZE]
        texts=[b[2] for b in batch]
        vectors=embed_texts(texts)
        collection.add(
            ids=[f"{b[0]}::{b[1]}" for b in batch],  # 唯一 ID
            documents=texts,
            embeddings=vectors,
            metadatas=[{"source": b[0],
                        "section": b[1],
                        "doc_id": b[3].get("doc_id",""),
                        "doc_title": b[3].get("title",""),
                        "category": b[3].get("category",""),
                        "version": b[3].get("version",""),
                        } for b in batch],  # 溯源用
        )
        print(f"已入库 {min(i + BATCH_SIZE, len(chunks))}/{len(chunks)}")

    print(f"完成！向量库在 {CHROMA_DIR}")

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--reset", action="store_true", help="重建：先清空旧库")
    args = parser.parse_args()
    main(reset=args.reset)