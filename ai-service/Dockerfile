# ============================================================
#  电商订单智能客服 —— Python AI 服务镜像（FastAPI + DeepSeek）
# ============================================================
#  单独构建（平时不用手敲）：
#    docker build -t commerce-service/ai:local ./ai-service
#
#  ⚠️ 这个镜像里不含任何密钥。
#     DEEPSEEK_API_KEY / INTERNAL_TOKEN / JAVA_BASE_URL
#     全部由 docker-compose 在启动时用环境变量注入。
#     main.py 里的 load_dotenv() 找不到 .env 也不会报错，
#     它会安静地读环境变量 —— 这正是容器里想要的行为。
# ============================================================
FROM python:3.14-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

# 先装依赖、再拷代码：只要 requirements.txt 没变，
# 重新打镜像时这一层走缓存，省掉几分钟的 pip 下载。
# 用清华 PyPI 镜像，国内下载快。
COPY requirements.txt .
RUN pip install --no-cache-dir \
        -i https://pypi.tuna.tsinghua.edu.cn/simple \
        --trusted-host pypi.tuna.tsinghua.edu.cn \
        -r requirements.txt

# 提示词和工具定义已拆成独立文件，必须一起 COPY 进去
# （漏了会报 ModuleNotFoundError: No module named 'prompts'）
COPY main.py rag.py prompts.py tool_specs.py ./

# RAG：语料与建库脚本（镜像里不含向量库，容器启动时自动构建）
COPY ingest.py ./
COPY knowledge/ ./knowledge/

EXPOSE 8000

# 启动时先建向量库（38 条语料，约 10 秒）；建库失败不阻塞启动 ——
# 查订单/建工单等基础功能不受影响，只有政策检索会降级。
# --host 0.0.0.0 必须写！默认是 127.0.0.1，那只允许"容器自己"访问，
# 同一个 Docker 网络里的 java 容器就调不进来了。
CMD ["sh", "-c", "python ingest.py --reset || echo '[RAG] 向量库构建失败，政策检索暂不可用'; exec python -m uvicorn main:app --host 0.0.0.0 --port 8000"]
