"""
检索层评估：只看“找得准不准”，不看模型回答怎么样

【为什么先跑这层】
RAG的回答本质上是“检索内容的函数”——检索错了，后面一定错。
而且这层客观、可复现、几乎不花钱，适合反复跑

【两类题分开处理】
有标准答案的（normal/vague/conflict/long）->算Hit@k 和 MRR
没标准答案的（out_of_scope/injection）->只看最高相似度，用来定阈值
"""
import json
import sys
from datetime import datetime
from pathlib import Path
# ============================================================
# 一、路径准备 —— 让脚本能 import 到上一层的 rag.py
# ============================================================
#找到要操作的目录ai-service
BASE_DIR=Path(__file__).resolve().parent.parent
#python只在sys.path列出的目录里找模块。默认包含“脚本所在目录”（即eval/）
#但不包含ai-service/，所以手动把ai-service/插到最前面
sys.path.insert(0, str(BASE_DIR))
import rag

EVAL_DIR=Path(__file__).resolve().parent
GOLDEN=EVAL_DIR/"golden_set.json"
RESULT_DIR=EVAL_DIR/"results"

#两类题：能不能用标准答案命中
ANSWERABLE={"normal","vague","conflict","long"}
BOUNDARY={"out_of_scope","injection"}
#将标准输出设置为UTF-8
if sys.stdout.encoding and sys.stdout.encoding.lower()!="utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

# ============================================================
# 二、判定：标准答案排在第几位
# ============================================================
def rank_of(case,hits):
    """
    返回标准答案在检索结果里的排名（从1开始）；没命中返回0
    参数：
    case:数据集里的一道题，expect_docs是“可接受的小节”列表
    hits：rag.search_policy()的返回，已按相关度从高到低排好序
    为什么是两层循环？
    外层：按顺序看检索结果的第1、2、3...条
    内层：拿这一条和“所有可接受的小节”逐个比对
    一旦撞上任意一个，当前的外层次序号就是它的排名
    :param case:
    :param hits:
    :return:
    """
    for i,h in enumerate(hits,start=1):    #start=1:排名从1数起，不是从0
        for exp in case["expect_docs"]:
            if h["doc_title"]==exp["doc_title"] and h["section"]==exp["section"]:
                return i              #一命中就返回，后面的不用看了
    return 0                          #循环走完都没有命中
# ============================================================
# 三、主流程
# ============================================================
def main():
    #读数据集
    data=json.loads(GOLDEN.read_text(encoding="utf-8"))
    cases=data["cases"]
    #策略从命令行第一个参数取，不传就是vector
    strategy=sys.argv[1] if len(sys.argv)>1 else "vector"

    #每题多取几条来算指标，比实际用的3条宽--这样MRR才看得出排名的差异
    top_k=5
    #按类型分为两组
    answerable=[c for c in cases if c["type"] in ANSWERABLE]
    boundary= [c for c in cases if c["type"] in BOUNDARY]


    print("=" * 66)
    print(f"检索层评估   策略={strategy}   top_k={top_k}")
    print("=" * 66)
    # ---------- 第 1 部分：有标准答案的题 ----------
    rows=[]
    for c in answerable:
        hits=rag.search_policy(c["question"],top_k=top_k,strategy=strategy)
        rows.append({"case": c,"rank": rank_of(c,hits),"hits": hits})
    n=len(rows)
    # sum(1 for r in rows if 条件) 是 Python 惯用的计数写法：
    # 生成器每次产出一个 1，sum 把它们加起来 = 满足条件的条数。
    # 等价于：c = 0 / for r in rows: / if 条件: c += 1
    hit1 = sum(1 for r in rows if r["rank"] == 1)  # 排第 1 的题数
    hit3 = sum(1 for r in rows if 1 <= r["rank"] <= 3)  # 排进前 3 的题数
    hit5 = sum(1 for r in rows if 1 <= r["rank"] <= 5)  # 排进前 5 的题数
    # MRR = 每题得分的平均。得分规则：第 1 名得 1 分、第 2 名得 1/2、第 3 名得 1/3……
    # `if r["rank"] else 0` —— rank 是 0（没命中）时不能做 1/0，所以直接记 0 分
    mrr = sum((1 / r["rank"]) if r["rank"] else 0 for r in rows) / n

    print()
    print(f"【总体】共{n}题（有标准答案的）")
    #格式化说明：{hit1:2d}占2位右对齐；{x:5.1f}占5位保留1位小数
    print(f"  Hit@1   {hit1:2d}/{n} = {hit1 / n * 100:5.1f}%")
    print(f"  Hit@3   {hit3:2d}/{n} = {hit3 / n * 100:5.1f}%   ← 实际用 Top-3，主要看这个")
    print(f"  Hit@5   {hit5:2d}/{n} = {hit5 / n * 100:5.1f}%")
    print(f"  MRR     {mrr:.3f}")
    # ---------- 第 2 部分：按题型分组看 ----------
    # 总体分数会掩盖细节。分类型看才知道"是哪类问法拖了后腿"——
    # 比如 normal 满分但 vague 很差，说明检索对模糊表达不够鲁棒。
    print()
    print("【分类型】")
    print(f"  {'type':<14}{'题数':>5}{'Hit@1':>12}{'Hit@3':>12}{'MRR':>9}")
    by_type = {}
    for t in ["normal", "vague", "conflict", "long"]:
        g = [r for r in rows if r["case"]["type"] == t]  # 取出这一类的全部题
        if not g:
            continue  # 这一类没题就跳过
        g1 = sum(1 for r in g if r["rank"] == 1)
        g3 = sum(1 for r in g if 1 <= r["rank"] <= 3)
        gm = sum((1 / r["rank"]) if r["rank"] else 0 for r in g) / len(g)
        by_type[t] = {"n": len(g), "hit1": g1, "hit3": g3, "mrr": round(gm, 3)}
        # 显示成 "g1/len(g)" 而不是百分比 —— 题数少的时候（比如 long 只有 4 题），
        # 百分比会失真（1 题之差就是 25%），看绝对数更实在
        print(f"  {t:<14}{len(g):>5}{g1:>7}/{len(g)}"
              f"{g3:>9}/{len(g)}{gm:>9.3f}")

    # ---------- 第 3 部分：没有标准答案的题 ----------
    # 这类题不判命中（本来就没有正确答案），而是看"它们最高能骗到多少相似度"。
    # 如果边界题的相似度普遍低于正题，就可以设个门槛：低于它的检索结果不给模型用，
    # 让它回答"没查到"而不是硬答。
    print()
    print(f"【边界题（无标准答案）】共 {len(boundary)} 题 —— 看最高相似度，定门槛用")
    bnd = []
    for c in boundary:
        hits = rag.search_policy(c["question"], top_k=1, strategy=strategy)  # 只要第 1 条
        sim = hits[0]["similarity"] if hits else 0.0
        bnd.append({"id": c["id"], "type": c["type"], "sim": sim})
        # {c['question'][:28]:<30} 先截断到 28 字再左对齐占 30 位，让后面的数字对齐
        print(f"  [{c['type']:<12}] {c['question'][:28]:<30} 最高相似度 {sim:.3f}")

    if bnd:
        avg_bnd = sum(b["sim"] for b in bnd) / len(bnd)
        # 对照组：正题里"排第 1"的那些，它们的 Top-1 相似度是多少
        correct1 = [r for r in rows if r["rank"] == 1]
        avg_pos = sum(r["hits"][0]["similarity"] for r in correct1) / max(len(correct1), 1)
        print(f"  → 边界题平均 {avg_bnd:.3f}   正题命中时平均 {avg_pos:.3f}"
              f"   差 {(avg_pos - avg_bnd) * 100:.1f} 个千分点")

    # ---------- 第 4 部分：失分明细 ----------
    # 光看数字不知道问题在哪。这里把没进 Top-3 的题连同"实际检索到了什么"打出来，
    # 这些明细才是修语料、调参数的依据。
    missed = [r for r in rows if r["rank"] == 0 or r["rank"] > 3]
    if missed:
        print()
        print(f"【未进 Top-3（{len(missed)} 题）】")
        for r in missed:
            c = r["case"]
            print(f"  [{c['type']}] {c['id']}  {c['question'][:34]}")
            # 把 expect_docs 里所有可接受答案拼成一行，方便和实际结果对照
            print(f"       期望：{' 或 '.join(e['doc_title'] + '/' + e['section'] for e in c['expect_docs'])}")
            for i, h in enumerate(r["hits"][:3], start=1):
                print(f"       {i}. 《{h['doc_title']}》/ {h['section']}  ({h['similarity']})")

    # ---------- 第 5 部分：存档 ----------
    # 存 JSON 的意义：下次跑完可以直接和这次对比（baseline vs 优化后），
    # 而不是靠记忆或截图。文件名带时间戳，多次运行不会互相覆盖。
    RESULT_DIR.mkdir(exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = RESULT_DIR / f"retrieval-{strategy}-{stamp}.json"
    out.write_text(json.dumps({
        "layer": "retrieval",
        "strategy": strategy,
        "top_k": top_k,
        "time": stamp,
        "overall": {"n": n, "hit1": hit1, "hit3": hit3, "hit5": hit5, "mrr": round(mrr, 4)},
        "by_type": by_type,
        "boundary": bnd,
        # 明细只存关键的几项。不把完整 hits 存进来，否则文件会很大
        "missed": [{"id": r["case"]["id"], "type": r["case"]["type"],
                    "question": r["case"]["question"], "rank": r["rank"]} for r in missed],
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    #   ↑ ensure_ascii=False：中文按原样写，不转成 \uXXXX 编码
    print()
    print(f"结果已存：{out.relative_to(BASE_DIR)}")


if __name__ == "__main__":
    main()
