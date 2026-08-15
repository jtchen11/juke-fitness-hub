package com.gym.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult<T> {

    // 执行是否成功
    private boolean success;

    // 展示给用户的自然语言回复（必填）
    private String message;

    // 携带的结构化数据（比如课程列表、订单ID等）
    private T data;

    // 支付动作枚举，前端根据这个渲染按钮，彻底抛弃 **PAYMENT** 字符串
    private PaymentAction paymentAction;

    // 额外参数（比如支付选项列表、跳转链接等）
    private Map<String, Object> extra;

    // ---------- 静态工厂方法（方便构造） ----------
    public static <T> ToolResult<T> success(String message) {
        return new ToolResult<>(true, message, null, PaymentAction.NONE, null);
    }

    public static <T> ToolResult<T> success(String message, T data) {
        return new ToolResult<>(true, message, data, PaymentAction.NONE, null);
    }

    public static <T> ToolResult<T> fail(String message) {
        return new ToolResult<>(false, message, null, PaymentAction.NONE, null);
    }

    public static <T> ToolResult<T> askPayment(String message, Map<String, Object> extra) {
        return new ToolResult<>(true, message, null, PaymentAction.CONFIRM_PAYMENT, extra);
    }
}