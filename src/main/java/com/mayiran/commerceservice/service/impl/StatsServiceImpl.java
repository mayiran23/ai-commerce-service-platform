package com.mayiran.commerceservice.service.impl;

import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.enums.RoleEnum;
import com.mayiran.commerceservice.mapper.OrderMapper;
import com.mayiran.commerceservice.service.StatsService;
import com.mayiran.commerceservice.vo.OrderStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsServiceImpl implements StatsService {
    @Autowired
    private OrderMapper orderMapper;
    @Override
    public OrderStatsVO getStats() {
        // ---- 越权控制：和列表接口同一套白名单写法（fail-safe）----
        // 客服/管理员本来就要看全站，userId 保持 null → SQL 里不加条件
        // 其余角色（含 role 为 null 的异常情况）一律锁成"只能看自己"
        String role = UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);

        Long userId = null;
        if (!canSeeAll) {
            userId = UserContext.getUserId();
        }
        List<Map<String,Object>> rows = orderMapper.countByStatus(userId);
        Map<String,Long> byStatus=new LinkedHashMap<>();
        long total=0L;
        //遍历map集合
        for (Map<String,Object> row : rows) {
            // 防御性处理：正常 GROUP BY 出来的 n 不会是 null，
            // 但万一数据异常，宁可显示 0 也不要因为拆箱炸掉整个统计接口
            String status= String.valueOf(row.get("status"));
            Number raw = (Number) row.get("n");
            Long n = raw == null ? 0L : raw.longValue();

            byStatus.put(status,n);
            total+=n;
        }
        return OrderStatsVO.builder()
                .total(total)
                .byStatus(byStatus)
                .build();
    }
}
