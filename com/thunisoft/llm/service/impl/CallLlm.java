/**
 * @projectName intelligenteditor
 * @package com.thunisoft.llm.service.impl
 * @className com.thunisoft.llm.service.impl.CallLlm
 * @copyright Copyright 2025 Thunisoft, Inc All rights reserved.
 */
package com.thunisoft.llm.service.impl;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.eclipse.jetty.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONException;
import com.thunisoft.intelligenteditor.service.llm.LlmConfigService;
import com.thunisoft.intelligenteditor.service.prompt.ReplaceWordService;
import com.thunisoft.intelligenteditor.util.SessionUtil;
import com.thunisoft.llm.domain.ReplaceWord;
import com.thunisoft.llm.domain.ReplaceWords;
import com.thunisoft.llm.service.ChatLlmParams;
import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.util.FixedSizeQueue;


/**
 * CallLlm
 * @description
 * @author huayu
 * @date 2025/1/7 14:14
 */


@Service
public class CallLlm implements ICallLlm, CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(CallLlm.class);

    private boolean debug = false;
    private String modeName;
    private String thinkModeName;
    private String token;
    private String thinkToken;

    private double temperature;
    private int top_k;
    private double top_p;
    private double presence_penalty;
    private double frequency_penalty;
    private double repetition_penalty;

    private String thinkModelType = "common";
    private String baseModelType = "common";

    private boolean stream;
    private boolean contianThinkLable;
    private boolean forceThink;
    private String url;
    private String thinkUrl;
    private boolean replaceEnable;


    @Value("${jw.loginEnabled:false}")
    private boolean jwLoginEnabled;

    @Autowired
    ChatLlmParams chatLlmParams;
    @Autowired
    SessionUtil sessionUtil;

    @Value("${chat.llmparams.socketTimeout:30000}")
    private int socketTimeout;

    @Value("${chat.llmparams.connectTimeout:30000}")
    private int connectTimeout;


    // 本地测试用
    /*
    private void initParams(){
        this.debug = false;
        this.modeName = "qwen3-30b-a3b-instruct-2507";
        this.thinkModeName = "qwen3-30b-a3b-thinking-2507";
        this.token = "sk-7b7e0e6749d14e8b83381e0b8ac809e5";
        this.thinkToken = "sk-7b7e0e6749d14e8b83381e0b8ac809e5";
        this.temperature = 0;
        this.top_k = -1;
        this.top_p = 0.9;
        this.presence_penalty = 1;
        this.frequency_penalty = 1;
        this.repetition_penalty = 1;
        this.stream = true;
        this.contianThinkLable = false;
        this.forceThink = false;
        this.url = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        this.thinkUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        this.replaceEnable = false;
        this.thinkModelType = "common";
        this.baseModelType = "common";
    }
    */
    private void initParams(){
        chatLlmParams.initParams();
        this.debug = chatLlmParams.getDebug();
        this.modeName = chatLlmParams.getModeName();
        this.thinkModeName = chatLlmParams.getThinkModeName();
        this.token = chatLlmParams.getToken();
        this.thinkToken = chatLlmParams.getThinkToken();
        this.temperature = chatLlmParams.getTemperature();
        this.top_k = chatLlmParams.getTop_k();
        this.top_p = chatLlmParams.getTop_p();
        this.presence_penalty = chatLlmParams.getPresence_penalty();
        this.frequency_penalty = chatLlmParams.getFrequency_penalty();
        this.repetition_penalty = chatLlmParams.getRepetition_penalty();
        this.stream = chatLlmParams.getStream();
        this.contianThinkLable = chatLlmParams.getContianThinkLable();
        this.forceThink = chatLlmParams.getForceThink();
        this.url = chatLlmParams.getUrl();
        this.thinkUrl = chatLlmParams.getThinkUrl();
        this.replaceEnable = chatLlmParams.getReplaceEnable() != null && chatLlmParams.getReplaceEnable();
        this.thinkModelType = chatLlmParams.getThinkModelType();
        this.baseModelType = chatLlmParams.getBaseModelType();
    }
 
    @Autowired
    private ReplaceWordService replaceWordService;

    @Autowired
    private LlmConfigService llmConfigService;

    private List<ReplaceWord> wpsReplace = new ArrayList<>();
    private ReplaceWords replaceWords = new ReplaceWords();

    @Override
    public void run(String... args) throws Exception {
        replaceWords.addAllReplaceWords(replaceWordService.selectAll());
    }

    public void initializeData(){
        replaceWords.clearAllReplaceWords();
        replaceWords.addAllReplaceWords(replaceWordService.selectAll());
    }

    @Override
    public String callOpenAiInterface(boolean useThink, int maxToken, boolean isExchange, JSONArray messages, JSONObject extParams, OutputStream outputStream) {
        initParams();
        String answer = "";
        String thinkStep = "";
        long s = System.currentTimeMillis();
        // 构建参数
        JSONObject params = buildParams(messages, maxToken, isExchange, useThink);
        if(StringUtils.equals(thinkModelType, "fusionModel") || StringUtils.equals(baseModelType, "fusionModel")){
            messages.getJSONObject(0).put("content", messages.getJSONObject(0).getString("content") + (useThink? "/think":"/no_think"));
        }
        String url = buildUrl(useThink);
        boolean thinking = false;
        boolean isStop = false;
        boolean allToThink = false;
        if(extParams.containsKey("allToThink")){
            allToThink = extParams.getBooleanValue("allToThink");
        }
        // xman: 如果模型是glm-4，则设置response_format 为 json_object 才能正确的输出json格式
        if(extParams.containsKey("response_format") && params.getString("model").toLowerCase().startsWith("glm-4")){
            String responseFormat = extParams.getString("response_format");
            if(StringUtils.isBlank(responseFormat) || ! responseFormat.equals("json_object") || ! responseFormat.equals("text")){
                responseFormat = "text";
            }
            params.put("response_format", new JSONObject().put("type", responseFormat));
        }
        // xman: qwen3 是否开启思考模式
        if(useThink && extParams.containsKey("enable_thinking") && params.getString("model").toLowerCase().startsWith("qwen3")) {
            params.put("enable_thinking", extParams.getBooleanValue("enable_thinking"));
        }
        // xman: qwen3 可以限制思考长度
        if(useThink && extParams.containsKey("thinking_budget") && params.getString("model").toLowerCase().startsWith("qwen3")){
            params.put("thinking_budget", extParams.getInteger("thinking_budget"));
        }
        // xman: 如果只是给内部程序使用，则只需要输出思考过程到用户界面，不输出回答到用户界面
        boolean onlyThinking = false;
        if(extParams.containsKey("only_thinking") && extParams.getBooleanValue("only_thinking")){
            onlyThinking = true;
        }
        // 开始调用
        try(CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, (chain, authType) -> true).build())
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build()) {
            HttpPost httpPost = new HttpPost(url);
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout).build();
            httpPost.setConfig(requestConfig);
            String jsonString = params.toString();

            if(jwLoginEnabled){
                String userid = sessionUtil.getUserid();
                String token= sessionUtil.getToken();
                httpPost.setHeader("User-Id", userid);
                httpPost.setHeader("Token", token);
            }
            httpPost.setEntity(new StringEntity(jsonString, "UTF-8"));
            httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
            httpPost.setHeader("Authorization", "Bearer " + (useThink? this.thinkToken: this.token));
            httpPost.setHeader("appkey", (useThink? this.thinkToken: this.token));
            httpPost.setHeader("X-Mt-Authorization", (useThink? this.thinkToken: this.token));
            if(debug){
                logger.info("message = {}", messages);
            }
            try (CloseableHttpResponse httpResponse = httpclient.execute(httpPost)) {
                HttpEntity httpEntity = httpResponse.getEntity();

                FixedSizeQueue<String> fixedSizeQueue = new FixedSizeQueue<>(5);

                if (!stream) {
                    // 非流式输出
                    String result = IOUtils.toString(httpEntity.getContent(), StandardCharsets.UTF_8);
                    try {
                        JSONObject jsonObject = JSONObject.parseObject(result);
                        JSONObject object = jsonObject.getJSONArray("choices").getJSONObject(0);

                        JSONObject messagesInfo = object.getJSONObject("message");
                        if(messagesInfo.containsKey("reasoning_content")){
                            String content = object.getJSONObject("message").getString("reasoning_content");
                            String r = replaceHalfPunc(content);
                            answer += r;
                            outputStream.write(r.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }else{
                            String content = object.getJSONObject("message").getString("content");
                            String r = replaceHalfPunc(content);
                            answer += r;
                            outputStream.write(r.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    } catch(JSONException e) {
                        logger.error("【大模型调用】非流式结果解析异常，解析内容：{}", result, e);
                    }
                        catch (Exception e) {
                        logger.error("【大模型调用】非流式结果调用异常，模型名称是{}", useThink? thinkModeName: modeName, e);
                    }
                } else {
                    // 流式输出
                    boolean isFirstOutput = true;
                    InputStreamReader reader = new InputStreamReader(httpEntity.getContent(), StandardCharsets.UTF_8);
                    char[] buff = new char[2048];
                    int length = 0;
                    String prestr = "";
                    String preSentence = "";

                    String temp = "";           // 临时字符串，用于处理中断问题
                    while ((length = reader.read(buff)) != -1) {
                        if(debug){
                            logger.info("【大模型调用-debug】 从接口接收到句子长度是：{}", length);
                        }
                        if(isStop){
                            if(debug){
                                logger.info("【大模型调用-debug】 执行break");
                            }
                            break;
                        }
                        if(fixedSizeQueue.isSame()){
                            logger.error("模型开始不停的重复输出，输出的内容为{}，break", fixedSizeQueue);
                            break;
                        }

                        String resultPre = new String(buff, 0, length).replace("data: [DONE]", "");
                        if(StringUtils.contains(resultPre, "event:message") && StringUtils.contains(resultPre, "id:")){
                            String[]  arrays = resultPre.split("\n");
                            resultPre = "";
                            for (String array : arrays) {
                                if(!StringUtils.startsWith(array, "event:") && !StringUtils.startsWith(array, "id:") && StringUtils.startsWith(array, "data:")){
                                    resultPre += (array + "\n");
                                }
                            }
                        }
                        if(debug){
                            logger.info("【大模型调用-debug】 当前输出的句子是：{}", resultPre);
                        }
                        if(!resultPre.trim().endsWith("}") ){
                            temp += resultPre;
                            continue;
                        }
                        if(resultPre.trim().endsWith("}") && !temp.equals("")){
                            temp += resultPre;
                        }
                        if(temp.trim().endsWith("}")){
                            resultPre = temp;
                            temp = "";
                        }
                        // 整合完成
                        String[] resultArr = resultPre.split("\n");
                        for (String result : resultArr) {
                            if(StringUtils.isBlank(result)){
                                continue;
                            }
                            JSONObject jsonObject = new JSONObject();
                            try{
                                if(StringUtils.equals(result, ": keep-alive")){
                                    continue;
                                }
                                result = result.replace("data:", "").replace("data: ", "").replace("[DONE]", "");
                                if((StringUtils.equals(baseModelType, "fusionModel") || StringUtils.equals(thinkModelType, "fusionModel")) && !useThink){
                                    result = result.replace("</think>", "").replace("<think>", "");
                                }
                                if(StringUtils.isNotBlank(result)){
                                    // xman: 解析错误时，跳过
                                    try {
                                        jsonObject = JSONObject.parseObject(result);
                                    } catch (JSONException e) {
                                        logger.error("【大模型】调用流式大模型返回的结果解析异常，解析的内容：{}", result, e);
                                        continue;
                                    }
                                    if(jsonObject.containsKey("choices")){
                                        if(jsonObject.getJSONArray("choices").size() == 1){
                                            JSONObject delta = ((JSONObject)jsonObject.getJSONArray("choices").get(0)).getJSONObject("delta");
                                            //这里兼容一下强制思考的模式
                                            if(forceThink && delta.containsKey("role") && StringUtils.equals(delta.getString("role"), "assistant")){
                                                if((!delta.containsKey("content") || StringUtils.isBlank(delta.getString("content")) )
                                                        && !delta.containsKey("reasoning_content") && isFirstOutput){
                                                    logger.info("强制思考模式， 放入一个<think>");
                                                    isFirstOutput = false;
                                                    delta.put("content", "<think>");
                                                } else if (forceThink && !delta.containsKey("reasoning_content") && isFirstOutput && delta.containsKey("content") && StringUtils.isNotBlank(delta.getString("content"))) {
                                                    logger.info("isFirst = {}, delta = {}", isFirstOutput,  delta);
                                                    isFirstOutput = false;
                                                    delta.put("content", "<think>" + delta.getString("content"));
                                                }
                                            }
                                            if(delta.containsKey("reasoning_content") && StringUtils.isNotBlank(delta.getString("reasoning_content"))){
                                                // 处理思维过程
                                                String resiningData = delta.getString("reasoning_content");
                                                resiningData = replaceHalfPunc(unicodeToCh(resiningData));
                                                thinkStep += resiningData;
                                                outputStream.write(("<think>" + resiningData + "</think>").getBytes(StandardCharsets.UTF_8));
                                                outputStream.flush();
                                            }else{
                                                String data = delta.getString("content");
                                                if(StringUtils.isNotEmpty(data) || "\n".equals(data)|| "\n\n".equals(data)) {
                                                    data = unicodeToCh(data);
                                                    if(StringUtils.equals("\n", prestr) && StringUtils.equals("\n", data)) {
                                                        // 跳过双换行
                                                        continue;
                                                    }
                                                    try {
                                                        String r = replaceHalfPunc(data);
                                                        if(isEndWithPun(r) || preSentence.length() > 50){
                                                            if(StringUtils.endsWith(prestr, "\n") && StringUtils.startsWith(r, "\n")){
                                                                r = r.substring(1);
                                                            }
                                                            preSentence += r;
                                                            preSentence = format(preSentence, wpsReplace);
                                                            if(contianThinkLable){
                                                                if(preSentence.contains("<think>")){
                                                                    thinking = true;
                                                                    String rescontent = preSentence.replace("<think>", "").replace("</think>", "").trim();
                                                                    thinkStep += rescontent;
                                                                    outputStream.write(("<think>" + rescontent + "</think>").getBytes(StandardCharsets.UTF_8));
                                                                    outputStream.flush();
                                                                    preSentence = "";
                                                                    continue;
                                                                }
                                                                if(thinking){
                                                                    if(StringUtils.contains(preSentence, "</think>")){
                                                                        thinking = false;
                                                                        String[] rescontentArray = preSentence.split("</think>");
                                                                        if(rescontentArray.length == 2){
                                                                            thinkStep += rescontentArray[0];
                                                                            preSentence = rescontentArray[1];
                                                                            String rescontent = rescontentArray[0].replace("<think>", "").replace("</think>", "");
                                                                            outputStream.write(("<think>" + rescontent + "</think>").getBytes(StandardCharsets.UTF_8));
                                                                            outputStream.flush();
                                                                        }
                                                                    }else{
                                                                        String rescontent = preSentence.replace("<think>", "").replace("</think>", "");
                                                                        thinkStep += rescontent;
                                                                        outputStream.write(("<think>" + rescontent + "</think>").getBytes(StandardCharsets.UTF_8));
                                                                        outputStream.flush();
                                                                        preSentence = "";
                                                                        continue;
                                                                    }

                                                                }
                                                            }
                                                            answer += preSentence;
                                                            fixedSizeQueue.add(preSentence);
                                                            for(int i = 0; i < preSentence.length() / 3 + 1; i++){
                                                                String tm = preSentence.substring(i * 3, Math.min((i * 3 + 3), preSentence.length()));
                                                                if(allToThink){
                                                                    outputStream.write(("<think>" + tm + "</think>").getBytes(StandardCharsets.UTF_8));
                                                                }else{
                                                                    // xman: 如果只需要输出思考过程，则不输出回答
                                                                    if(onlyThinking) continue;
                                                                    outputStream.write(tm.getBytes(StandardCharsets.UTF_8));
                                                                }
                                                                outputStream.flush();
                                                            }
                                                            prestr = r;
                                                            preSentence = "";
                                                        }else{
                                                            if(StringUtils.endsWith(prestr, "\n") && StringUtils.startsWith(r, "\n")){
                                                                r = r.substring(1);
                                                            }
                                                            preSentence += r;
                                                            prestr = r;
                                                        }
                                                    }catch (IOException e){
                                                        // xman: 只有输出流写数据失败失败时，关闭输出流，并退出循环
                                                        logger.error("【大模型】调用大模型过程中客户端关闭流，向输出流写数据失败！", e);
                                                        outputStream.close();
                                                        break;
                                                    }catch (Exception e) {
                                                        logger.error("【大模型】大模型生成过程出错！内容：{}", result, e);
                                                        continue;
                                                    }
                                                }
                                            }
                                        }
                                    } else if(jsonObject.containsKey("result") && jsonObject.containsKey("code")
                                            && StringUtils.equals(jsonObject.getString("code"), "200")){
                                        // 兼容一下黑龙江的定制接口
                                        String data = jsonObject.getJSONObject("result").getString("answer");
                                        if(StringUtils.isNotEmpty(data) || "\n".equals(data)|| "\n\n".equals(data)) {
                                            data = unicodeToCh(data);
                                            if(StringUtils.equals("\n", prestr) && StringUtils.equals("\n", data)) {
                                                // 跳过双换行
                                                continue;
                                            }
                                            try {
                                                String r = replaceHalfPunc(data);
                                                if(isEndWithPun(r) || preSentence.length() > 50){
                                                    if(StringUtils.endsWith(prestr, "\n") && StringUtils.startsWith(r, "\n")){
                                                        r = r.substring(1);
                                                    }
                                                    preSentence += r;
                                                    preSentence = format(preSentence, wpsReplace);

                                                    if(contianThinkLable){
                                                        if(preSentence.contains("<think>")){
                                                            thinking = true;
                                                            String rescontent = preSentence.replace("<think>", "").replace("</think>", "").trim();
                                                            thinkStep += rescontent;
                                                            outputStream.write(("<think>" + rescontent + "</think>").getBytes(StandardCharsets.UTF_8));
                                                            outputStream.flush();
                                                            preSentence = "";
                                                            continue;
                                                        }
                                                        if(thinking){
                                                            if(StringUtils.contains(preSentence, "</think>")){
                                                                thinking = false;
                                                                String[] rescontentArray = preSentence.split("</think>");
                                                                if(rescontentArray.length == 2){
                                                                    thinkStep += rescontentArray[0];
                                                                    preSentence = rescontentArray[1];
                                                                    String rescontent = rescontentArray[0].replace("<think>", "").replace("</think>", "");
                                                                    outputStream.write(("<think>" + rescontent + "</think>").getBytes(StandardCharsets.UTF_8));
                                                                    outputStream.flush();
                                                                }
                                                            }else{
                                                                String rescontent = preSentence.replace("<think>", "").replace("</think>", "");
                                                                thinkStep += rescontent;
                                                                outputStream.write(("<think>" + rescontent + "</think>").getBytes(StandardCharsets.UTF_8));
                                                                outputStream.flush();
                                                                preSentence = "";
                                                                continue;
                                                            }

                                                        }
                                                    }
                                                    answer += preSentence;
                                                    fixedSizeQueue.add(preSentence);
                                                    for(int i = 0; i < preSentence.length() / 3 + 1; i++){
                                                        String tm = preSentence.substring(i * 3, Math.min((i * 3 + 3), preSentence.length()));
                                                        // xman: 如果只需要输出思考过程，则不输出回答
                                                        if(onlyThinking) continue;
                                                        outputStream.write(tm.getBytes(StandardCharsets.UTF_8));
                                                        outputStream.flush();
                                                    }
                                                    prestr = r;
                                                    preSentence = "";
                                                }else{
                                                    if(StringUtils.endsWith(prestr, "\n") && StringUtils.startsWith(r, "\n")){
                                                        r = r.substring(1);
                                                    }
                                                    preSentence += r;
                                                    prestr = r;
                                                }
                                            }catch (IOException e){
                                                // xman: 只有输出流写数据失败失败时，关闭输出流，并退出循环
                                                logger.error("【大模型】调用大模型过程中客户端关闭流，向输出流写数据失败！", e);
                                                outputStream.close();
                                                break;
                                            }catch (Exception e) {
                                                logger.error("【大模型】大模型生成过程出错！内容：{}", result, e);
                                                continue;
                                            }
                                        }
                                        try {
                                            boolean is_finished = jsonObject.getBooleanValue("is_finished");
                                            if(is_finished){
                                                isStop = true;
                                                if(debug){
                                                    logger.info("【大模型】大模型break2");
                                                }
                                                if(StringUtils.isNotBlank(preSentence)){
                                                    preSentence = format(preSentence, wpsReplace);
                                                    // xman: 如果只需要输出思考过程，则不输出回答
                                                    if(onlyThinking) continue;
                                                    outputStream.write(preSentence.getBytes(StandardCharsets.UTF_8));
                                                    outputStream.flush();
                                                    answer += preSentence;
                                                }
                                                //这里处理最后的行
                                                reader.close();
                                                return StringUtils.isNotBlank(thinkStep)? ("<think>\n" + thinkStep + "</think>" + answer) : answer;
                                            }

                                        }catch (Exception e){
                                            logger.error("获取参数失败", e);
                                        }
                                    }else {
                                        logger.error("大模型生成返回的状态码异常, code = {},message = {},  收到的字符串是：{}",
                                                jsonObject.containsKey("code")? jsonObject.getInteger("code") : "",
                                                        jsonObject.containsKey("message")? jsonObject.getString("message") : "", result);
                                    }
                                }
                            }catch (Exception e){
                                logger.error("【大模型】调用流式大模型返回的结果解析异常，解析的内容是{}", resultPre, e);
                            }
                        }
                    }
                    //最后一句发送
                    if(StringUtils.isNotBlank(preSentence)){
                        preSentence = format(preSentence, wpsReplace);
                        if(allToThink){
                            outputStream.write(("<think>" + preSentence + "</think>").getBytes(StandardCharsets.UTF_8));
                        }else{
                            // xman: 如果只需要输出思考过程，则不输出回答
                            if(! onlyThinking) 
                                outputStream.write(preSentence.getBytes(StandardCharsets.UTF_8));
                        }
                        outputStream.flush();
                        answer += preSentence;
                    }
                    //这里处理最后的行
                    reader.close();
                    if(debug){
                        logger.info("【大模型】closereader");
                    }
                    if(StringUtils.isBlank(answer)){
                        outputStream.write("努力思考中！请稍后提问...".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }catch (Exception e){
            logger.error("【大模型调用】 大模型调用异常", e);
        }
        logger.info("【大模型调用】通过大模型完成对话完成，耗时{}",(System.currentTimeMillis() - s));
        if(debug){
            logger.info("【大模型调用调试】输入大模型的信息是 {}, 输出的结果是 {}", params, answer);
        }
        return StringUtils.isNotBlank(thinkStep)? ("<think>\n" + thinkStep + "</think>" + answer) : answer;
    }

    @Deprecated
    @Override
    public String callLlmHuayuInterface(int maxToken, boolean isExchange, JSONArray messages, OutputStream outputStream) {
        return null;
    }


    /**
     * 构建参数
     * @param messages
     * @param maxToken
     * @return
     */
    private JSONObject buildParams(JSONArray messages, int maxToken, boolean isExchage, boolean useThink){
        JSONObject ext = new JSONObject();
        if(messages.size() == 3 && messages.getJSONObject(2).containsKey("name") && messages.getJSONObject(2).getString("name").equals("ext")){
            ext = messages.getJSONObject(2);
            messages.remove(2);
        }

        JSONObject think = new JSONObject();
        if(StringUtils.equals(this.thinkModelType, "ds3.1") && useThink){
            think.put("type", "enabled");
        }
        if(StringUtils.equals(this.baseModelType, "ds3.1") && !useThink){
            think.put("type", "disabled");
        }

        JSONArray msgs = formatMessage(messages);
        JSONObject params = new JSONObject();
        params.put("messages", msgs);
        if(think.keySet().size() > 0){
            params.put("thinking", think);
        }
        params.put("model", useThink? this.thinkModeName: this.modeName);
        params.put("temperature", isExchage? 0.9: this.temperature);
        params.put("top_p", this.top_p);
        if(ext.keySet().size() > 1){
            // 遍历一下并赋值
            ext.remove("name");
            //            params.put("extra_body", ext);
            for(String key : ext.keySet()){
                params.put(key, ext.get(key));
            }
        }
        if(this.top_k != -1){
            params.put("top_k", this.top_k);
        }
        if(this.presence_penalty != -10){
            params.put("presence_penalty", this.presence_penalty);
        }
        params.put("repetition_penalty", this.repetition_penalty);
        params.put("stream", this.stream);
        if(maxToken > 0){
            params.put("max_tokens", maxToken);
        }
        return params;
    }

    private JSONArray formatMessage(JSONArray messages) {
        JSONArray newMessages = new JSONArray();
        for (int i = 0; i < messages.size(); i++) {
            JSONObject msg = messages.getJSONObject(i);
            String content = msg.getString("content");
            if (content != null && content.trim().length() > 0) {
                JSONObject newMsg = new JSONObject();
                newMsg.put("role", msg.getString("role"));
                try {
                    // xman: 本地测试用
                    // int halfMaxInput = 8192 * 2;
                    int halfMaxInput = (int) (chatLlmParams.getMaxInput() / 2);
                    if (content.length() > halfMaxInput) {
                        logger.info("【大模型】获取最大输入长度：{}， 文字长度{}", halfMaxInput * 2, content.length());
                        content = content.substring(0, halfMaxInput);
                    }
                } catch (Exception e) {
                    logger.error("【大模型】获取最大输入长度失败", e);
                }
                newMsg.put("content", content);
                newMessages.add(newMsg);
            }
        }
        return newMessages;
    }


    private String buildUrl(boolean useThink){
        String postUrl = (useThink? thinkUrl: url) ;
        if(StringUtils.endsWith(url, "/v1")){
            postUrl = (useThink? thinkUrl: url) + "/chat/completions";
        }
        return postUrl;
    }


    private String replaceHalfPunc(String text){
        if (!replaceEnable){
            return text;
        }
        return text.replace(":", "：")
                .replace(",", "，")
                .replace(";", "；")
                .replace("(", "（")
                .replace(")", "）")
                .replace(" ", "");
    }

    private String unicodeToCh(String string) {
        Pattern pattern = Pattern.compile("(\\\\u(\\p{XDigit}{4}))");
        Matcher matcher = pattern.matcher(string);
        char ch;
        while(matcher.find()) {
            ch = (char) Integer.parseInt(matcher.group(2), 16);
            string = string.replace(matcher.group(1), ch + "");
        }
        string = string.replaceAll("\n{1,}", "\n");
        return string;
    }


    private boolean isEndWithPun(String key){
        return StringUtils.endsWith(key, "，") || StringUtils.endsWith(key, "。") || StringUtils.endsWith(key, "！")|| StringUtils.endsWith(key, "\n");
    }


    private String format(String sentence, List<ReplaceWord> wpsReplace){
        sentence = sentence
                .replace("\n\n", "\n")
                .replace("\\n\\n", "\\n")
                .replace("\\n", "\n")
                .replace("<br>", "")
                .replace("**", "")
                .replace("\\u3000", "")
                .replace("标题：", "")
                .replace("</br>", "\n")
                .replace("【决定】：", "")
                .replace("【摘要】：", "")
                .replace("【通告】：", "")
                .replace("【报告】：", "")
                .replace("【乡村振兴】：", "")
                .replace("【决定】", "")
                .replace("【摘要】", "")
                .replace("【通告】", "")
                .replace("【报告】", "")
                .replace("【乡村振兴】", "")
                .replace("【", "")
                .replace("发文时间：", "")
                .replace("  ", "")
                .replace(" ", "")
                .replace("  ", "")
                .replace(" ", "")
                .replace("  ", "")
                .replace(" ", "")
                .replace("  ", "")
                .replace(" ", "")
                .replace("】", "");
        sentence = replaceWords.replace(sentence, new ArrayList<>());
        if(debug){
            logger.info("【大模型调试-debug】替换后的句子是： 【{}】", sentence);
        }
        //替换调图片url的正则
        String regex = "!\\[.*?\\](?:\\(|（).*?(?:\\)|）)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(sentence);
        sentence = matcher.replaceAll("");
        return sentence;
    }

}
