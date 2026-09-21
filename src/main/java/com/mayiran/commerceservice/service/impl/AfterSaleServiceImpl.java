package com.mayiran.commerceservice.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.AfterSalePageDTO;
import com.mayiran.commerceservice.enums.AfterSaleStatus;
import com.mayiran.commerceservice.enums.RoleEnum;
import com.mayiran.commerceservice.mapper.AfterSaleMapper;
import com.mayiran.commerceservice.result.PageResult;
import com.mayiran.commerceservice.service.AfterSaleService;
import com.mayiran.commerceservice.vo.AfterSalePageVO;
import com.mayiran.commerceservice.vo.AfterSaleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {
    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Override
    public AfterSalePageVO pageAfterSales(AfterSalePageDTO afterSalePageDTO) {
        //越权控制
        String role= UserContext.getRole();
        boolean canSeeAll = RoleEnum.AGENT.name().equals(role) || RoleEnum.ADMIN.name().equals(role);

        if (!canSeeAll) {
            afterSalePageDTO.setUserId(UserContext.getUserId());
        } else {
            // 工单接口的参数表里没有 userId，客服也不该按用户筛 → 清掉前端可能塞进来的脏值
            afterSalePageDTO.setUserId(null);
        }
        //设置分页码和每页的记录数
        PageHelper.startPage(afterSalePageDTO.getPage(),afterSalePageDTO.getLimit());
        Page<AfterSaleVO> page=afterSaleMapper.pageAfterSales(afterSalePageDTO);
        PageInfo<AfterSaleVO> pageInfo=new PageInfo<>(page);
        List<AfterSaleVO> records = pageInfo.getList();

        //补statusText
        for (AfterSaleVO vo : records){
            vo.setStatusText(AfterSaleStatus.textOf(vo.getStatus()));
        }

        //查状态统计
        List<Map<String,Long>> rows = afterSaleMapper.countByStatus(canSeeAll?null:UserContext.getUserId());
        //先将7个状态全摆成0,再用SQL结果覆盖
        Map<String,Long> stats =new LinkedHashMap<>();
        for (AfterSaleStatus s : AfterSaleStatus.values()){
            stats.put(s.name(),0L);
        }

        for(Map<String,Long> row : rows){
            //设置状态
            String status= String.valueOf(row.get("status"));
            //设置状态对应的工单数
            Long n=row.get("n");
            stats.put(status,n);
        }

        //组装返回
        return AfterSalePageVO.builder()
                .total(pageInfo.getTotal())
                .records(records)
                .stats(stats)
                .build();
    }
}
