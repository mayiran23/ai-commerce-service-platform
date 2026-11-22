# 1）基础镜像
FROM python:3.14-slim

# 2) 工作目录
WORKDIR /app

# 3) python 运行参数
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYHTONUNBUFFERED=1

# 4) 先拷依赖清单
COPY requirements.txt .

# 5) 装依赖（走国内源）
RUN pip install --no-cache-dir -r requirements.txt \
        -i https://pypi.tuna.tsinghua.edu.cn/simple

# 6) 再拷代码
COPY . .

# 7) 声明端口
EXPOSE 8000

# 8) 启动命令
CMD ["uvicorn","main:app","--host","0.0.0.0","--port","8000"]
