package com.mayiran.commerceservice.mapper;

import com.github.pagehelper.Page;
import com.mayiran.commerceservice.dto.OrderPageDTO;
import com.mayiran.commerceservice.dto.SearchDTO;
import com.mayiran.commerceservice.entity.OrderItem;
import com.mayiran.commerceservice.entity.OrderLogistics;
import com.mayiran.commerceservice.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    //订单的分页查询
    Page<OrderVO> pageOrders(OrderPageDTO orderPageDTO);

    //根据订单号查询订单的详细信息
    OrderVO getByOrderNo(@Param("orderNo") String orderNo);

    //根据订单号查询商品的信息,并且要计算退货资格
    List<OrderItem> getItems(@Param("orderNo") String orderNo);

    //根据订单号查询物流的信息
    OrderLogistics getLogistics(@Param("orderNo") String orderNo);

    //批量查询多个订单的商品明细（给列表接口用，避免每行订单都单独查一次数据库）
    //入参是一个订单号集合，比如 ["SO001","SO002","SO003"]
    List<OrderItem> getItemsByOrderNos(@Param("orderNos") List<String> orderNos);

    //统计各状态的数量
    /**
     * 按订单状态分组统计数量。
     *
     * ⚠️ 泛型值是 Object 而不是 Long：这个 Map 里混着两种类型 ——
     *    status 是 String，n 是 COUNT(*) 的 Long。
     *    写成 Map<String,Long> 能通过编译（类型擦除），但那是假的类型安全：
     *    谁哪天写一句 Long s = row.get("status") 就会在运行期炸 ClassCastException。
     *    如实写 Object，取出来自己做转换，才是诚实的做法。
     */
    List<Map<String, Object>> countByStatus(@Param("userId") Long userId);

    List<OrderVO> searchForInternal(SearchDTO searchDTO);
}
