# 读取配置
import os
import time
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI
from openai import OpenAI
from pydantic import BaseModel

BASE_DIR=Path(__file__).resolve().parent   #本文件所在目录
load_dotenv(BASE_DIR / '.env')             #指定.env的绝对路径

#下面两行,让HTTP库访问本地地址时不要走系统代理
os.environ.setdefault("NO_PROXY","127.0.0.1,localhost")
os.environ.setdefault("no_proxy","127.0.0.1,localhost")

API_KEY=os.getenv("DEEPSEEK_API_KEY","")
MODEL=os.getenv("DEEPSEEK_MODEL","deepseek-chat")
BASE_URL=os.getenv("DEEPSEEK_BASE_URL","https://api.deepseek.com")

#客户端见一次全局复用,不用每次请求都新建
client=OpenAI(api_key=API_KEY,base_url=BASE_URL)

#系统提示词
SYSTEM_PROMPT=(
    "你是一名电商平台的智能客服助手,负责解答订单,物流,退换货相关问题."
    "回答要简洁,口语化,不要编造订单信息."
    "如果用户问的具体订单而你没有查到数据,就如实返回没有查到数据,并提示用户提供订单号."
)

app=FastAPI(title="AI Commerce Service",version="0.1.0")

#请求体的格式
class ChatRequest(BaseModel):
    """对应 Java 传来的 {"userId":1,"sessionId":"S2026...","message":"..."}"""
    userId: int  # 必填。由 Java 从 JWT 解析后注入，前端伪造不了
    sessionId: str | None = None  # 可选。传 None 表示新建会话
    message: str  # 必填。用户问的那句话

#两个接口
@app.get("/health")
def health():
    """体检接口:服务器是否活着,配置有没有读到"""
    return {
        "status": "ok",
        "model":MODEL,
        "hasKey":bool(API_KEY),
    }

@app.post("/chat")
def chat(rep: ChatRequest):
    """核心接口:收一句话,让大模型答一句"""
    started=time.time()    #记下开始时间,最后算耗时

    completion=client.chat.completions.create(
        model=MODEL,
        #messages是对话历史,今天只有两条:系统设定+用户这一句
        messages=[
            {"role":"system","content":SYSTEM_PROMPT},
            {"role":"user","content":rep.message},
        ],
        #temperature控制发散程度:0最稳,1最天马行空.客服需要稳
        temperature=0.3,
        #超时设30秒
        timeout=30,
    )
    #取出最终答案
    answer=completion.choices[0].message.content
    #返回
    return {
        "answer":answer,
        "intent":"UNKNOWN",
        "confidence":0.0,
        "latencyMs":int((time.time()-started)*1000),
        "tools":[],
    }