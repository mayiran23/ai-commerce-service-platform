package com.mayiran.commerceservice.vo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单详情（GET /api/after-sales/{ticketNo} 的 data）
 *
 * 比列表多三样：关联订单快照、流转记录、可流转状态。
 *
 * 为什么用【继承】而不是把 AfterSaleVO 塞成一个字段？
 * 因为契约里详情的字段是【平铺】在顶层的（ticketNo、status、reason 都在最外层），
 * 继承之后 Jackson 序列化出来正好就是平铺的，不用手工搬字段。
 *
 * ⚠️ 注意是 @SuperBuilder 而不是 @Builder：
 * 父类 AfterSaleVO 也必须用 @SuperBuilder，子类才能拿到【父类的字段】。
 * 若父类用 @Builder、子类用 @Builder，Lombok 会闷声丢掉父类那 17 个字段，
 * 编译器只给一行 WARNING，不报错 —— 这是个很难查的坑。
 *
 * ⚠️ @EqualsAndHashCode(callSuper = true) 也不能省：
 * @Data 默认生成的 equals/hashCode 只看子类自己的 3 个字段，
 * 父类的 ticketNo、status 全被忽略，两个不同工单可能被判成"相等"。
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class AfterSaleDetailVO extends AfterSaleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联订单快照，前端用它显示"这个工单是哪一单的哪件商品" */
    private OrderVO order;

    /**
     * 流转记录，按 create_time 升序（首条 fromStatus 为 null）。
     *
     * @Builder.Default 不能省：否则 @SuperBuilder 会无视右边的 new ArrayList<>()，
     * 用 builder 构建时 flows 是 null，前端 flows.map(...) 直接报错。
     */
    @Builder.Default
    private List<AfterSaleFlowVO> flows = new ArrayList<>();

    /**
     * 当前状态下允许流转到的目标状态，如 ["COMPLETED"]。
     * 前端靠它决定显示哪几个按钮；后端在流转接口里还会再校验一次（后端才是权威）。
     */
    private List<String> allowedTransitions;
}