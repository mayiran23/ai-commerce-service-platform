package com.mayiran.commerceservice.service.impl;

import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.dto.ChatSessionDTO;
import com.mayiran.commerceservice.entity.ChatMessage;
import com.mayiran.commerceservice.entity.ChatSession;
import com.mayiran.commerceservice.exception.ChatSessionNotFoundException;
import com.mayiran.commerceservice.mapper.ChatMessageMapper;
import com.mayiran.commerceservice.mapper.ChatSessionMapper;
import com.mayiran.commerceservice.service.ChatService;
import com.mayiran.commerceservice.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private RestClient aiRestClient;
    @Autowired
    private ObjectMapper objectMapper;
    //多轮对话上下文最多带最近十条
    private static final int HISTORY_LIMIT=10;
    //单条历史消息最多回传500字
    private static final int HISTORY_ITEM_MAX=500;

    // ---------- 流式对话专用 ----------
    // Python AI 服务的地址，从 application.properties 的 ai.service.url 读
    @Value("${ai.service.url}")
    private String aiServiceUrl;

    /**
     * 流式对话线程池。
     *
     * 为什么要单独开池子，不复用 Tomcat 的请求线程：
     *   流式请求会一直挂着（一条回答要 3~8 秒），如果用 Tomcat 线程，
     *   并发几个用户就能把 200 个工作线程占满，整个服务会被拖死。
     *   单独池子最多 8 个并发流，超出的排队，把风险限制在聊天功能内部。
     *
     * 用守护线程：服务关闭时不会因为还有流没跑完而卡住。
     */
    private static final ExecutorService STREAM_POOL =
            Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "chat-stream");
                t.setDaemon(true);
                return t;
            });

    /**
     * 调 Python 流式接口用的 JDK 原生 HttpClient。
     *
     * 为什么不用项目里那个 RestClient：
     *   RestClient 是**同步**客户端，它会把响应体整个读完才返回，
     *   用它接 SSE 等于把流式彻底废掉（会等到最后一个字才一起收到）。
     *   而 JDK 11+ 自带的 HttpClient 支持 BodyHandlers.ofInputStream()，
     *   能拿到"还没结束"的流边走边读，且不用引任何新依赖。
     */
    private static final java.net.http.HttpClient STREAM_HTTP =
            java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    // 🔴 必须强制 HTTP/1.1，否则请求体在传输中丢失。
                    //
                    // JDK HttpClient 默认优先 HTTP/2，对 http:// 地址会先发一个
                    // `Upgrade: h2c` 请求尝试升级协议。而 uvicorn 不支持明文 HTTP/2，
                    // 升级握手处理不一致，结果 POST 的 body 直接丢了 ——
                    // FastAPI 报 `422 {"loc":["body"],"msg":"Field required","input":null}`，
                    // 看起来像"没传参数"，其实是协议层把参数吃了。
                    // uvicorn 日志里那两行 `Unsupported upgrade request` /
                    // `Invalid HTTP request received` 就是铁证。
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .build();

    @Override
    public ChatReplyVO chat(Long userId, ChatSendDTO chatSendDTO) {
        //1:拿到一个合法的会话号
        String sessionId=chatSendDTO.getSessionId();
        //判断会话号是否为空
        if(sessionId==null||sessionId.isBlank()){
            //为空则创建会话号
            sessionId=createSession(userId);
        }else {
            ChatSession session =chatSessionMapper.getBySessionId(sessionId);
            if(session==null||!session.getUserId().equals(userId)){
                throw new ChatSessionNotFoundException(MessageConstant.SESSION_NOT_FOUND);
            }
        }

        //2:先取最近几轮上下文
        List<Map<String,String>> history=loadHistory(sessionId);
        //3:先把用户这句话落库
        chatMessageMapper.insert(ChatMessage.builder()
                .sessionId(sessionId)
                .role("user")
                .content(chatSendDTO.getMessage())
                .createTime(LocalDateTime.now())
                .build());

        //4:调Python AI服务,失败就降级
        ChatReplyVO reply;

        try{
            Map<String,Object> body=new HashMap<>();
            body.put("userId",userId);
            body.put("sessionId",sessionId);
            body.put("message",chatSendDTO.getMessage());
            body.put("history",history);

            reply=aiRestClient.post()
                    .uri("/chat")
                    .body(body)
                    .retrieve()
                    .body(ChatReplyVO.class);
        }catch (Exception e){
            //AI挂了不能把整个对话功能带崩
            log.error("AI服务调用失败,sessionId:{}",sessionId,e);
            reply=ChatReplyVO.fallback("客服繁忙,请稍后再试或转人工");
        }
        if(reply==null){
            reply=ChatReplyVO.fallback("客服繁忙,请稍后再试或转人工");
        }

        //4:把AI的回复也落库
        chatMessageMapper.insert(ChatMessage.builder()
                .sessionId(sessionId)
                .role("assistant")
                .intent(reply.getIntent())
                .content(reply.getAnswer())
                .toolCalls(toJson(reply.getTools()))
                        .confidence(reply.getConfidence()==null? BigDecimal.ZERO:reply.getConfidence())
                        .latencyMs(reply.getLatencyMs())
                .createTime(LocalDateTime.now())
                .build());

        //5:会话号必须返回给前端

        reply.setSessionId(sessionId);
        return reply;
    }

    // ==========================================================
    //  流式对话
    // ==========================================================

    @Override
    public SseEmitter chatStream(Long userId, ChatSendDTO chatSendDTO) {
        // 超时给 180 秒：大模型 + 工具链最慢也就十几秒，
        // 但用户网络差时读写会很慢，留足余量。到点 Spring 会自动关掉连接。
        SseEmitter emitter = new SseEmitter(180_000L);

        // ⚠️ userId 是**参数**，不是在这里再调 UserContext.getUserId()。
        //    这个方法返回后请求线程就释放了，下面线程池里的代码
        //    根本看不到 ThreadLocal 里的东西。
        STREAM_POOL.execute(() -> {
            try {
                doChatStream(userId, chatSendDTO, emitter);
            } catch (ChatSessionNotFoundException e) {
                // 会话不存在/越权：这是能明确告诉用户的原因
                sendQuietly(emitter, Map.of("type", "error", "message", e.getMessage()));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式对话失败,userId:{}", userId, e);
                sendQuietly(emitter, Map.of("type", "error", "message", "客服繁忙，请稍后再试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void doChatStream(Long userId, ChatSendDTO dto, SseEmitter emitter) throws Exception {
        // ---------- 1. 会话校验 / 新建（和同步版完全一致） ----------
        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = createSession(userId);
        } else {
            ChatSession session = chatSessionMapper.getBySessionId(sessionId);
            if (session == null || !session.getUserId().equals(userId)) {
                throw new ChatSessionNotFoundException(MessageConstant.SESSION_NOT_FOUND);
            }
        }

        // ---------- 2. 取多轮上下文 ----------
        List<Map<String, String>> history = loadHistory(sessionId);

        // ---------- 3. 用户这句话先落库 ----------
        chatMessageMapper.insert(ChatMessage.builder()
                .sessionId(sessionId)
                .role("user")
                .content(dto.getMessage())
                .createTime(LocalDateTime.now())
                .build());

        // ---------- 4. 先把 sessionId 推给前端 ----------
        // 新建会话时，前端必须立刻拿到 sessionId，否则这一轮的回复
        // 和下一条提问会被当成两个不同的会话。
        final String sid = sessionId;

        // ---------- 5. 请求 Python 的 /chat/stream ----------
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("sessionId", sessionId);
        body.put("message", dto.getMessage());
        body.put("history", history);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/chat/stream"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        Map<String, Object> doneEvent = null;
        long started = System.currentTimeMillis();

        HttpResponse<InputStream> response =
                STREAM_HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            // 把 Python 返回的错误详情打出来：FastAPI 的 422 会明确告诉你是哪个字段
            String errBody = "";
            try {
                errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception ignore) {
            }
            log.error("AI 流式接口返回异常状态码:{}, 请求体:{}, 错误详情:{}",
                    response.statusCode(), objectMapper.writeValueAsString(body), errBody);
            sendQuietly(emitter, Map.of("type", "error", "message", "客服繁忙，请稍后再试"));
            emitter.complete();
            return;
        }

        // ---------- 6. 边读边转发 ----------
        // try-with-resources 保证异常时也会关掉底层连接，不然 AI 那边
        // 会一直挂着一个没人读的响应，连接池迟早耗尽。
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // SSE 帧里只认以 "data:" 开头的行，空行是帧分隔符，跳过即可
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }

                // 原样转发给浏览器。用 TEXT_PLAIN 是故意的：
                // 如果让 Spring 按 JSON 序列化这个 String，它会给整个
                // JSON 串再套一层引号并转义，前端解析直接崩。
                emitter.send(SseEmitter.event().data(data, MediaType.TEXT_PLAIN));

                // 顺手把 done 事件留下来，为了稍后落库
                if (doneEvent == null) {
                    try {
                        Map<String, Object> evt = objectMapper.readValue(
                                data, new TypeReference<Map<String, Object>>() {});
                        if ("done".equals(evt.get("type"))) {
                            doneEvent = evt;
                        }
                    } catch (Exception ignore) {
                        // 转发是主线，解析失败不能影响它
                    }
                }
            }
        }

        emitter.complete();

        // ---------- 7. AI 的回复落库 ----------
        // 只能放在流结束之后：流式过程中拿不到完整文本。
        // 如果用户中途关页面，emitter.send 会抛异常并跳出循环，
        // 这里就不会执行 —— 宁可少一条记录，也不要存半句话。
        if (doneEvent != null) {
            saveAssistantMessage(sid, doneEvent);
        }

        log.info("流式对话结束,sessionId:{},耗时:{}ms", sid, System.currentTimeMillis() - started);
    }

    /** 把 done 事件里的完整信息写进 t_chat_message */
    private void saveAssistantMessage(String sessionId, Map<String, Object> done) {
        try {
            String answer = String.valueOf(done.getOrDefault("answer", ""));
            if (answer.isBlank()) {
                return;
            }
            Object conf = done.get("confidence");
            Object latency = done.get("latencyMs");

            chatMessageMapper.insert(ChatMessage.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .intent((String) done.get("intent"))
                    .content(answer)
                    .toolCalls(toJson(done.get("tools")))
                    .confidence(conf instanceof Number
                            ? new BigDecimal(conf.toString()) : BigDecimal.ZERO)
                    .latencyMs(latency instanceof Number
                            ? ((Number) latency).intValue() : null)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            // 落库失败不能影响用户已经看到的回答，只记日志
            log.error("流式回答落库失败,sessionId:{}", sessionId, e);
        }
    }

    /** 推事件给前端；连接已断时静默失败，不往外抛 */
    private void sendQuietly(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(
                    objectMapper.writeValueAsString(payload), MediaType.TEXT_PLAIN));
        } catch (Exception ignore) {
            // 浏览器已经关了页面，属于正常情况
        }
    }

    private List<Map<String, String>> loadHistory(String sessionId) {
        List<ChatMessage> recent = chatMessageMapper.getRecentBySessionId(sessionId, HISTORY_LIMIT);
        if(recent==null||recent.isEmpty()){
            return Collections.emptyList();
        }
        //SQL是倒叙取的,最近的在前,这里反转时间正序,模型按顺序读会好理解
        Collections.reverse(recent);

        List<Map<String,String>> history=new ArrayList<>(recent.size());

        for (ChatMessage m : recent) {
            if(m.getContent()==null||m.getContent().isBlank()){
                continue;
            }
            String content=m.getContent();
            if(content.length()>HISTORY_ITEM_MAX){
                content=content.substring(0, HISTORY_ITEM_MAX)+"...";
            }
            Map<String,String> item=new HashMap<>();
            item.put("role",m.getRole());
            item.put("content",content);
            history.add(item);
        }
        log.info("拼装多轮上下文,sessionId:{},共{}条",sessionId,history.size());
        return history;
    }

    @Override
    public ChatSessionIdVO getSessionId(ChatSessionDTO dto) {
        Long userId=UserContext.getUserId();

        String sessionId=createSession(userId);
        log.info("新建会话成功,userId:{},sessionId:{}",userId,sessionId);
        return ChatSessionIdVO.builder()
                .sessionId(sessionId)
                .build();
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        //角色同样从UserContext取
        String role = UserContext.getRole();
        boolean canQueryOthers="AGENT".equals(role)||"ADMIN".equals(role);

        Long targetUserId=canQueryOthers?userId:UserContext.getUserId();
        log.info("查询会话列表,当前用户:{},角色:{},实际查询userId:{}",UserContext.getUserId(),role,targetUserId);

        List<ChatSessionVO> sessions = chatSessionMapper.listByUserId(targetUserId);
        if(sessions==null||sessions.isEmpty()){
            return Collections.emptyList();
        }
        //库里存的是整段原文,列表只放一小截
        for(ChatSessionVO vo:sessions){
            vo.setTitle(summary(vo.getTitle(),30));
            vo.setLastMessage(summary(vo.getLastMessage(),40));
        }
        return sessions;
    }

    @Override
    public List<ChatMessageVO> getHistory(Long userId, String sessionId) {
        //1:参数检验
        if(sessionId==null||sessionId.isBlank()){
            throw new ChatSessionNotFoundException(MessageConstant.SESSION_NOT_FOUND);
        }

        //2:越权检验
        ChatSession session = chatSessionMapper.getBySessionId(sessionId);
        String role=UserContext.getRole();

        boolean canQuery="AGENT".equals(role)||"ADMIN".equals(role);
        if(session==null||(!canQuery&&!session.getUserId().equals(userId))){
            throw new ChatSessionNotFoundException(MessageConstant.SESSION_NOT_FOUND);
        }

        //3:取消息
        List<ChatMessage> messages = chatMessageMapper.getBySessionId(sessionId);

        //4:entity转vo
        List<ChatMessageVO> result=new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            result.add(ChatMessageVO.builder()
                    .role(m.getRole())
                    .content(m.getContent())
                    .intent(m.getIntent())
                    .confidence(m.getConfidence())
                    .latencyMs(m.getLatencyMs())
                    .createTime(m.getCreateTime())
                    .tools(parseTools(m.getToolCalls()))
                    .build());
        }
        log.info("查询历史消息,sessionId:{},共{}条",sessionId,result.size());
        return result;
    }


    /**
     * 把库里存的tool_calls JSON字符串还原成对象数组
     * @param json
     * @return
     */
    private List<ToolCallVO> parseTools(String json) {
        if(json==null||json.isBlank()){
            return Collections.emptyList();
        }
        try{
            return objectMapper.readValue(json, new TypeReference<List<ToolCallVO>>() {});
        }catch (Exception e){
            log.warn("工具调用记录反序列化失败,已忽略",e);
            return Collections.emptyList();
        }
    }

    /**
     * 截取字符串
     * @param text
     * @param max
     * @return
     */
    private String summary(String text, int max) {
        if(text==null){
            return null;
        }

        String flat=text.replaceAll("\\s+"," ").trim();
        if(flat.isEmpty()){
            return null;
        }

        return flat.length()<=max?flat:flat.substring(0,max)+"...";
    }

    //对象转JSON字符串
    private String toJson(Object obj) {
        if(obj==null){
            return null;
        }
        try{
            return objectMapper.writeValueAsString(obj);
        }catch (Exception e){
            log.warn("工具调用记录序列化失败,已忽略",e);
            return null;
        }
    }

    /**
     * 生成会话号,格式S+yyyyMMddHHmmss
     * @param userId
     * @return
     */
    private String createSession(Long userId) {
        String sessionId="S"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                +String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        LocalDateTime now=LocalDateTime.now();
        chatSessionMapper.insert(ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .createTime(now)
                .updateTime(now)
                .build());
        return sessionId;
    }
}
