/**
 * @projectName IntelligentEditor
 * @package com.thunisoft.intelligenteditor.controller.client
 * @className com.thunisoft.intelligenteditor.controller.client.FdqcController
 * @copyright Copyright 2024 Thunisoft, Inc All rights reserved.
 */
package com.thunisoft.intelligenteditor.controller.client;

import java.io.File;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.thunisoft.artery.engine.page.constants.PageParseConstants;
import com.thunisoft.artery.engine.page.context.ArterySpringContext;
import com.thunisoft.intelligenteditor.domain.zx.XzscResult;
import com.thunisoft.intelligenteditor.service.auditlog.IWenkuAuditLogService;
import com.thunisoft.intelligenteditor.service.config.ClientUIService;
import com.thunisoft.intelligenteditor.mapper.dao.Favorite_snptMapper;
import com.thunisoft.intelligenteditor.mapper.dao.SnptTextMapper;
import com.thunisoft.intelligenteditor.mapper.pojo.*;
import com.thunisoft.intelligenteditor.service.config.SysConfigService;
import com.thunisoft.intelligenteditor.service.dify.HmdPDocumentService;
import com.thunisoft.intelligenteditor.service.operate.TjUserOperateService;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.thunisoft.artery.component.base.Component;
import com.thunisoft.artery.component.form.pojo.OptionData;
import com.thunisoft.artery.data.QueryManager;
import com.thunisoft.artery.data.model.ArteryPageableData;
import com.thunisoft.artery.data.model.IQueryInfo;
import com.thunisoft.artery.security.ISecurityService;
import com.thunisoft.artery.util.ArteryDateUtil;
import com.thunisoft.artery.util.ArteryRequestUtil;
import com.thunisoft.artery.util.uuid.UUIDHelper;
import com.thunisoft.intelligenteditor.Constant;
import com.thunisoft.intelligenteditor.consts.Const;
import com.thunisoft.intelligenteditor.consts.ConstV2;
import com.thunisoft.intelligenteditor.consts.ZxConst;
import com.thunisoft.intelligenteditor.domain.UserCustomInfo;
import com.thunisoft.intelligenteditor.domain.searchresult.DifySearchRes;
import com.thunisoft.intelligenteditor.mapper.dao.AIFeedbackMapper;
import com.thunisoft.intelligenteditor.mapper.dao.UserDataMapper;
import com.thunisoft.intelligenteditor.service.ExcerptService;
import com.thunisoft.intelligenteditor.service.FavoriteInfoService;
import com.thunisoft.intelligenteditor.service.audio.AudioService;
import com.thunisoft.intelligenteditor.service.benefit.CheckBenefitService;
import com.thunisoft.intelligenteditor.service.chat.ChatLinkService;
import com.thunisoft.intelligenteditor.service.comparator.impl.CompareCache;
import com.thunisoft.intelligenteditor.service.feedback.AIFeedbackService;
import com.thunisoft.intelligenteditor.service.module.DataModuleService;
import com.thunisoft.intelligenteditor.service.prompt.TemplateInfoService;
import com.thunisoft.intelligenteditor.service.syjl.SyjlService;
import com.thunisoft.intelligenteditor.service.xzsc.IXzscService;
import com.thunisoft.intelligenteditor.service.zx.FwService;
import com.thunisoft.intelligenteditor.service.zx.PolicyService;
import com.thunisoft.intelligenteditor.service.zx.ZxBaseService;
import com.thunisoft.intelligenteditor.util.DateUtil;
import com.thunisoft.intelligenteditor.util.ExtractTxtUtil;
import com.thunisoft.intelligenteditor.util.FileUtil;
import com.thunisoft.intelligenteditor.util.NetUtils;
import com.thunisoft.intelligenteditor.util.SessionUtil;
import com.thunisoft.intelligenteditor.util.TextUtils;
import com.thunisoft.llm.domain.Answer;
import com.thunisoft.llm.domain.ChatParams;
import com.thunisoft.llm.domain.Gwwz;
import com.thunisoft.llm.domain.History;
import com.thunisoft.llm.domain.TemplateInfo;
import com.thunisoft.llm.domain.UserInfo;
import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.service.RagSearchService;
import com.thunisoft.llm.service.impl.GwwzService;
import com.thunisoft.llm.service.impl.promptImpl.CommonPromptService;
import com.thunisoft.llm.service.impl.promptImpl.QcPromptService;
import com.thunisoft.llm.util.HistoryUtil;
import com.thunisoft.llm.util.TextUtil;
import com.thunisoft.mcp.serveice.domain.EvallmResult;
import com.thunisoft.mcp.serveice.service.EvaService;
import com.thunisoft.tas.core.util.StringUtil;

import com.thunisoft.llm.writeragent.TemplateWriter;
import com.thunisoft.llm.writeragent.CollaborationWriter;

import static com.thunisoft.intelligenteditor.consts.UserDataConst.DATATYPE_ROLENAME;
import static com.thunisoft.intelligenteditor.consts.UserDataConst.SELECTDWXX;
import static com.thunisoft.intelligenteditor.consts.UserDataConst.SELECTDWXX_YES;
import static com.thunisoft.intelligenteditor.util.RSAUtil.getDecryptUserId;

/**
 * FdqcController Controller
 * 分段式起草

 * @author huayu
 * @date 2024-01-29
 * @version
 */
@Controller
@RequestMapping("client/fdqc")
public class FdqcController extends AIBaseController {

    private static Logger logger = LoggerFactory.getLogger(FdqcController.class);

    private static final Map<String, List<DifySearchRes>> _dify_search_data = new ConcurrentHashMap<>(5);

    @Autowired
    AIFeedbackMapper feedbackMapper;

    @Autowired
    IXzscService xzscService;
    @Autowired
    SyjlService syjlService;


    @Autowired
    ExcerptService excerptService;

    @Autowired
    ISecurityService securityService;

    @Autowired
    SessionUtil sessionUtil;

    @Autowired
    CheckBenefitService checkBenefitService;

    @Autowired
    ChatLinkService chatLinkService;

    @Autowired
    UserDataMapper userDataMapper;

    @Value("${chat.tips.domain}")
    String fwly;

    @Value("${chat.tips.fwjg}")
    String fwjg;

    @Value("${chat.tips.extInfo}")
    String extInfo;

    @Autowired
    FavoriteInfoService favoriteInfoService;

    @Autowired
    AIFeedbackService aiFeedbackService;

    @Value("${cocall.loginEnabled}")
    boolean isCoCall;

    @Value("${property.industry}")
    String industryName;


    @Autowired
    CompareCache compareCache;


    @Autowired
    FwService fwService;

    @Autowired
    PolicyService policyService;

    @Value("${cocall.loginEnabled:false}")
    private boolean cocallLoginEnabled;

    @Value("${intelli.sys.type}")
    String systype;

    @Value("${hmd.gwbd.hidden}")
    private boolean hmdGwbdHidden;





    @Autowired
    ICallLlm callLlm;

    @Autowired
    QcPromptService promptService;

    @Autowired
    GwwzService gwwzService;

    @Autowired
    DataModuleService dataModuleService;

    @Autowired
    CommonPromptService commonPromptService;


    @Autowired
    RagSearchService searchService;

    @Autowired
    TemplateInfoService templateService;

    @Autowired
    SysConfigService sysConfigService;

    @Autowired
    Favorite_snptMapper favoriteSnptMapper;

    @Autowired
    SnptTextMapper snptTextMapper;

    @Autowired
    EvaService evaService;

    @Autowired
    IWenkuAuditLogService auditLogService;

    @Autowired
    AudioService audioService;

    @Autowired
    private TjUserOperateService tjUserOperateService;


    @Value("${hmd.loginEnabled:false}")
    private boolean hmdLoginEnabled;

    @Value("${hmd.rag.enabled:false}")
    private boolean ragEnabled;


    @Value("${zzding.loginEnabled:false}")
    private boolean isZzdlogin;


    @Value("${zzding.rag.enabled:false}")
    private boolean zzdRagEnable;


    @Autowired
    private HmdPDocumentService hmdPDocumentService;


    /**
     * html模板入口
     * @param request 请求对象
     * @return 'resources/templates'下的html模板
     */
    @GetMapping()
    public String index(HttpServletRequest request, Model model) {
        String chatIdOpenValue = sysConfigService.getConfig("chat.identification.open");
        boolean chatIdOpen = StringUtils.isBlank(chatIdOpenValue) || Boolean.parseBoolean(chatIdOpenValue);
        Map customParams = new HashMap();
        customParams.put("isChatOpenLabel", chatIdOpen);
        model.addAttribute(PageParseConstants.ARTERY_CUSTOM_PARAMS, customParams);
        return "client/fdqc";
    }




    private boolean isZzdRag(){
        return zzdRagEnable && isZzdlogin;
    }


    private boolean isHmdRag(){
        return ragEnabled && hmdLoginEnabled;
    }


    /**
     * 点击时脚本(atyButtonEpssa)
     *
     * @return
     */
    @RequestMapping("/atyButtonEpssa/onClickServer")
    @ResponseBody
    public Object atyButtonEpssaOnClickServer() {
        String wz = ArteryRequestUtil.getParamString("gwwz");
        Gwwz gwwz = gwwzService.getGwwz(wz);
        if(gwwz != null){
            return gwwz.getTemplate();
        }
        return null;
    }

    /**
     * 上传时脚本(atyUploadAejkv)
     *
     * @return
     */
    @RequestMapping("/atyUploadAejkv1/onUploadUrl")
    @ResponseBody
    public JSONArray atyUploadAejkvOnUploadUrl(String fileupload) {
        if(StringUtils.isNotBlank(fileupload) && fileupload.contains("..")){
            logger.warn("上传相对路径参数中含有..，忽略请求");
            return new JSONArray();
        }
        String uploadFolder = ArteryRequestUtil.getUploadFolder(fileupload);
        if (StringUtils.isNotBlank(uploadFolder)) {
            File folder = new File(uploadFolder);
            if (folder.isDirectory()) {
                File[] files = folder.listFiles();
                JSONArray result = new JSONArray();
                for(File file : files) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("name", file.getName());
                    jsonObject.put("path", file.getAbsolutePath());
                    jsonObject.put("type", "ckgs");
                    result.add(jsonObject);
                }
                return result;
            }
        }
        return new JSONArray();
    }

    /**
     * 上传时脚本(atyUploadAejkv)
     *
     * @return
     */
    @RequestMapping("/atyUploadAejkv2/onUploadUrl")
    @ResponseBody
    public JSONArray atyUploadAejkv2OnUploadUrl(String fileupload) {
        if(StringUtils.isNotBlank(fileupload) && fileupload.contains("..")){
            logger.warn("上传相对路径参数中含有..，忽略请求");
            return new JSONArray();
        }
        String uploadFolder = ArteryRequestUtil.getUploadFolder(fileupload);
        if (StringUtils.isNotBlank(uploadFolder)) {
            File folder = new File(uploadFolder);
            if (folder.isDirectory()) {
                File[] files = folder.listFiles();
                JSONArray result = new JSONArray();
                for(File file : files) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("name", file.getName());
                    jsonObject.put("path", file.getAbsolutePath());
                    jsonObject.put("type", "cknr");
                    result.add(jsonObject);
                }
                return result;
            }
        }
        return new JSONArray();
    }


    /**
     * 聊天(chat)
     *  {
     *      id: 会话id
     *      tenantCode: 租户代码
     *      tenantId: 租户id
     *      loginId： 登录用户名
     *      simple： 最高简版页面嵌入的
     *      type: 起草文章的类别 （标题、大纲、全文）
     *      title： 起草文章的标题 必填
     *      cause： 文章事由
     *      continueWriteTime：第几次续写
     *      continueWriteTempText： 续写前的文章内容
     *      gwwz： 公文文种（15种正式公文和其他的事务公文名称）
     *      filepaths： 参考文章上传的路径由upload控件返回 list
     *      zhailuids: 摘录文章的id 列表 list
     *      shoucangids: 收藏文章的id 列表 list
     *      luyinids: 录音的id 列表 list
     *      abstract: 用于起草文章的摘要内容
     *      outline： 用于起草文章的大纲信息
     *      exchange： 是否是换一换操作
     *      isWps： 是否是wps里使用的
     *      isUseDwxx: 是否使用单位信息（从数据库里获取的信息）
     *  }
     * @param request
     * @param response
     */
    @RequestMapping(value = "/chat", method = RequestMethod.POST)
    @ResponseBody
    public void chat(HttpServletRequest request, HttpServletResponse response) {
        try {
            String requestBody = IOUtils.toString(request.getInputStream(), "UTF-8");
            JSONObject params = JSONObject.parseObject(requestBody);
            boolean checkBenefit = !StringUtils.equals("true", params.getString("simple"));
            callChat(params, response, false, null, checkBenefit);
        }catch(Exception e){
            logger.error("解析参数失败！", e);
        }
    }

    public void callChat(JSONObject params, HttpServletResponse response, boolean isOpenCall, String errorResponse,
                         boolean checkBenefit) {
        // 处理用户信息
        UserInfo userInfo = buildUserInfo(params, isOpenCall);
        String id = params.getString("id");
        // 权益校验
        try {
            if (checkBenefit && !checkBenefitService.check(Const.FUNC_ZNXZ, userInfo.getTenantId(), userInfo.getCorpid(), userInfo.getLoginId())) {
                response.setHeader("Content-Type", "text/html; charset=UTF-8");
                response.setHeader("Transfer-Encoding", "chunked");
                String message = Const.MSG_ZNXZ;
                response.getOutputStream().write(message.getBytes("UTF-8"));
                response.getOutputStream().flush();
                return;
            }
        }catch (Exception e){
            logger.error("【分段起草】权益校验失败", e);
        }
        // 构建参数
        String continueWriteTempText = params.containsKey("continueWriteTempText")? params.getString("continueWriteTempText") : "";
        int continueWriteTime = params.containsKey("continueWriteTime")? params.getInteger("continueWriteTime") : 0;
        ChatParams chatParams = buildChatParams(params, userInfo);
        chatParams.setLoginId(userInfo.getLoginId());
        try {
            if(chatParams.isNeedSearch()){
//                searchService.search(chatParams, chatParams.isNeedFineSearch());
                String dataId = params.getString("onlinesearchDataId");
                if(StringUtils.isNotBlank(dataId) && _dify_search_data.containsKey(dataId)) {
                    List<DifySearchRes> data = _dify_search_data.remove(dataId);
                    StringBuffer stringBuffer = new StringBuffer();
                    data.forEach(_data -> stringBuffer.append(StringUtils.isBlank(_data.getContent())?"":_data.getContent()).append("\n"));
                    stringBuffer.append(StringUtils.isBlank(chatParams.getBriefReference())?"":chatParams.getBriefReference());
                    chatParams.setBriefReference(stringBuffer.toString());
                }
            }
        }catch (Exception e){
            logger.error("深度搜索写作内容失败", e);
        }
        // 调用服务
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setHeader("Transfer-Encoding", "chunked");
        // 调用服务
        Answer answer = new Answer();
        try(OutputStream output = response.getOutputStream()){
            StringBuilder answer_content = new StringBuilder();
            StringBuilder prompt_content = new StringBuilder();
            if (chatParams.getType().contains(ConstV2.QW) 
                    && (StringUtils.equals(chatParams.getGwwz(), "方案") || StringUtils.equals(chatParams.getGwwz(), "工作总结")) 
                    && chatParams.getReferences() != null && chatParams.getReferences().size() > 0)  {
                // xman:协作写作(参考范文全文写作，需要提供参考内容，类型为方案或工作总结)
                output.flush();
                CollaborationWriter collaborationWriter = new CollaborationWriter(callLlm, chatParams.isUseThink(), -1, chatParams.isExchange());
                answer_content.append(collaborationWriter.writeArticle(chatParams, output));
                prompt_content.append("[{\"role\": \"system\", \"content\": \"协作\"},{\"role\": \"user\", \"content\": \"示例\"}]");
            } else if (chatParams.getType().contains(ConstV2.QW) && StringUtils.isBlank(chatParams.getOutline()) && StringUtils.isNotBlank(chatParams.getImitative()) )  {
                // xman: 仿写优化(参考范文全文写作，需要提供范文，不包括大纲)
                output.flush();
                TemplateWriter templateWriter = new TemplateWriter(callLlm, chatParams.isUseThink(), -1, chatParams.isExchange());
                answer_content.append(templateWriter.writeArticle(chatParams, output));
                prompt_content.append("[{\"role\": \"system\", \"content\": \"仿写\"},{\"role\": \"user\", \"content\": \"示例\"}]");
            } else {
                JSONObject inputParamsBlock = promptService.buildPrompt(chatParams, continueWriteTime, continueWriteTempText, output);
                JSONArray prompts = inputParamsBlock.getJSONArray("prompts");

                for(int i = 0; i < prompts.size(); i++){
                    JSONObject inputParams = prompts.getJSONObject(i);
                    answer_content.append(callLlm.callOpenAiInterface(chatParams.isUseThink(), inputParams.getInteger("maxtoken"), inputParams.getBooleanValue("exchange"), inputParams.getJSONArray("messages"), new JSONObject(), output));
                    prompt_content.append(JSONObject.toJSONString(inputParams.getJSONArray("messages")));
                    output.write("\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            }
            answer.setAnswer(answer_content.toString());
            answer.setPrompt(prompt_content.toString());
            answer.setQuestion(prompt_content.toString());
            // 记录信息
            saveRequestInfo(id, continueWriteTime, answer, userInfo.getCorpid(), userInfo.getLoginId());
            Map<String, Object> ext = new HashMap<>();
            ext.put("answer", answer_content.toString());
            auditLogService.saveAuditLog("公文写作", "公文写作", prompt_content.toString(), "成功", ext);
        }catch (Exception e){
            logger.error("【分段起草】 往输出流里写信息失败", e);
            writebackResponse(response, errorResponse);
        }
    }


    private void saveRequestInfo(String id, int continueWriteTime, Answer answer, String corpid, String loginId) {
        if(StringUtils.isNotBlank(id)) {
            if(continueWriteTime > 0) {
                // 更新结果和最后修改时间
                updateQiCaoRecord(id, answer);
            }else {
                saveQiCaoRecord(id, answer, corpid, loginId);
            }
        }else {
            logger.error("id为空，无法保存记录！");
        }
    }


    public  void writebackResponse(HttpServletResponse response, String errorResponse) {
        if(errorResponse == null) return;
        try {
            response.setHeader("Content-Type", "application/json; charset=UTF-8");
//                    response.setHeader("Transfer-Encoding", "chunked");
            String message = errorResponse;
            response.getOutputStream().write(message.getBytes("UTF-8"));
            response.getOutputStream().flush();
        }catch (Exception e2){
            logger.error("回写错误信息时出错", e2);
        }
    }

    private List<String> getShoucangInfos(String userId, String shoucangids) {
        List<String> result = new ArrayList<>();
        String[] ids = shoucangids.split(",");
        List<String> gwkids = new ArrayList<>();
        List<String> lucenids = new ArrayList<>();
        List<String> xzscIds = new ArrayList<>();
        List<String> hmdids = new ArrayList<>();
        for(String id: ids){
            if(id.contains("gwk_")){
                gwkids.add(id.replace("gwk_",""));
            }else if(id.contains("lucence_")){
                lucenids.add(id.replace("lucence_",""));
            }else if(id.contains("hmd_")){
                hmdids.add(id.replace("hmd_",""));
            }else {
                xzscIds.add(id);
            }
        }
        for(String id: gwkids){
            String content = favoriteInfoService.searchById(userId, id);
            if(StringUtils.isNotBlank(content.trim())){
                result.add(content);
            }
        }
        for(String id: lucenids){
            String content = favoriteInfoService.searchLucenceById(id);
            if(StringUtils.isNotBlank(content.trim())){
                result.add(content);
            }
        }
        for(String id: xzscIds){
            GwbjXzsc gwbjXzsc= xzscService.findXzscById(id);
            String content = gwbjXzsc.getContent();
            if(StringUtils.isNotBlank(content.trim())){
                result.add(content);
            }
        }
        for(String id: hmdids){
            JSONObject content = hmdPDocumentService.queryTrunksByFilePath(id, isHmdRag());
            StringBuffer stringBuffer = new StringBuffer();
            if(content.containsKey("data") && content.get("data") instanceof JSONArray && content.getJSONArray("data").size() > 0){
                JSONArray dataArray = content.getJSONArray("data");
                for(int i = 0; i < dataArray.size(); i++){
                    JSONObject data = dataArray.getJSONObject(i);
                    if(data.containsKey("text") && StringUtils.isNotBlank(data.getString("text").trim())){
                        stringBuffer.append(data.getString("text"));
                    }
                }
            }
            if(stringBuffer.length() > 0){
                result.add(stringBuffer.toString());
            }
        }
        return result;
    }

    /**
     * 获取摘抄的信息
     * @param zhailuids
     * @return
     */
    private String getZhailuInfo(String userId, String zhailuids) {
        String result = "";
        String[] ids = zhailuids.split(",");
        for(String id: ids){
            Excerpt excerpt = excerptService.selectById(userId, id);
            if(excerpt != null) result += (excerpt.getContent() + "\n");
        }
        return result;
    }



    /**
     * 获取摘抄的信息
     * @param
     * @return
     */
    private String getLuyinInfo(String luyinIds) {
        String result = "";
        for(String id: luyinIds.split(",")){
            result += audioService.getSummaryInfoById(id) + "\n";
        }
        return result;
    }

    @SuppressWarnings("unused")
    private History getContinueWriteHistory(String id, int continueWriteTime, String continueWriteTempText) {
        AIFeedback feedback = feedbackMapper.selectByPrimaryKey(id);
        if(feedback != null && continueWriteTime > 0) {
            JSONArray jsonArray = new JSONArray();
            if(continueWriteTime == 1) {
                jsonArray.add(new String[] {feedback.getPrompt(), feedback.getAnswer()});
            }else {
                jsonArray.add(new String[] {"继续", continueWriteTempText});
            }
            return HistoryUtil.jsonArray2History(jsonArray);
        }else {
            logger.error("没有查询到{}记录，无法获取历史！", id);
            return null;
        }
    }

    private void saveQiCaoRecord(String id, Answer answer, String corpid, String loginId) {
        AIFeedback feedback = new AIFeedback();
        feedback.setId(id);
        feedback.setCorpid(corpid);
        feedback.setUserid(loginId);
        feedback.setGroupId(UUIDHelper.getUuid());
        feedback.setType("起草");
        feedback.setInput(aiFeedbackService.convertContent2X(answer.getQuestion()));
        feedback.setPrompt(aiFeedbackService.convertContent2X(answer.getPrompt()));
        feedback.setAnswer(aiFeedbackService.convertContent2X(answer.getAnswer()));
        int wordSize = 0;
        if(answer.getAnswer()!=null) {
            wordSize = TextUtil.removeThinkTag(answer.getAnswer()).length();
        }
        feedback.setJgzs(wordSize);
        Date time = new Date();
        feedback.setLastmodify(ArteryDateUtil.formatDateTime(time));
        String nyr = DateUtil.formatTime(time, Const.STAT_TIMEFORAMT_NYR);
        feedback.setNyr(nyr);
        String ny = DateUtil.formatTime(time, Const.STAT_TIMEFORAMT_NY);
        feedback.setNy(ny);
        feedback.setIp(NetUtils.getClientIp(ArteryRequestUtil.getRequest()));
        feedback.setTime(time);
        feedbackMapper.insert(feedback);
        // 插入记录到t_tjfx，用来统计使用人数
        String ip = NetUtils.getClientIp(ArteryRequestUtil.getRequest());
        syjlService.addSyjl(Const.FUNC_ZNXZ, ip, corpid, loginId);
        tjUserOperateService.insert(loginId, corpid, ip, Const.ZNXZ_TJ, String.valueOf(wordSize), time, ny, nyr);
    }
    private void updateQiCaoRecord(String id, Answer answer) {
        AIFeedback feedback = feedbackMapper.selectByPrimaryKey(id);
        if(feedback != null) {
            String text = feedback.getAnswer()+"\n"+answer.getAnswer();
            feedback.setAnswer(text);
            feedback.setJgzs(TextUtil.removeThinkTag(text).length());
            feedback.setLastmodify(ArteryDateUtil.curDateTimeStr());
            feedbackMapper.updateByPrimaryKey(feedback);
        }else {
            logger.error("没有查询到{}记录，无法更新！");
        }
    }

    /**
     * 获得选择控件的值(atyToggleLayoutPymgu)
     * 点击 选择文种 返回的信息
     * @return 摘要，大纲，全文
     */
    @RequestMapping("/atyToggleLayoutPymgu/onSelectServer")
    @ResponseBody
    public Object atyToggleLayoutPymguOnSelectServer() {
        String gwwz = ArteryRequestUtil.getParamString("gwwz");
        String template = ArteryRequestUtil.getParamString("template");
        Gwwz gwwz1 = gwwzService.getGwwz(gwwz, template);
        return gwwz1;
    }


    /**
     * 数据源查询方法
     * 修改数据源的数据
     * @return Object 当为分页查询时，需要返回IPagableData对象，否则可为任意pojo
     */
    public List<OptionData> checkboxOptions() {

        String startTime = ArteryRequestUtil.getParamString("startTime");
        String endTime = ArteryRequestUtil.getParamString("endTime");
        String loginId = ArteryRequestUtil.getParamString("loginId");
        String queryStr = ArteryRequestUtil.getParamString("query");
        String type = ArteryRequestUtil.getParamString("type");
        if (StringUtils.isNotBlank(loginId)) {
            loginId = getDecryptUserId(loginId);
        }
        loginId = sessionUtil.getLoginId(loginId);
        List<OptionData> objects = new ArrayList<>();
        if(startTime == null || endTime == null){
            return objects;
        }
        List<Excerpt> excerpts = new ArrayList<>();
        if(StringUtils.equals(type, "scnr")){
            //获取收藏的信息
            if(StringUtils.isNotBlank(queryStr)){
                excerpts = favoriteInfoService.selectByDateDesc(loginId, queryStr, formatTime(startTime, 0), formatTime(endTime, 1));
            }else{
                excerpts = favoriteInfoService.selectByDateDesc(loginId, formatTime(startTime, 0), formatTime(endTime, 1));
            }
        }else{
            //这里调用 函数，把数据获取出来
            excerpts = excerptService.selectByDateDesc(loginId, formatTime(startTime, 0), formatTime(endTime, 1));
        }
        for(Excerpt excerpt : excerpts){
            objects.add(new OptionData(excerpt.getId(), formatContent(excerpt.getSource(), excerpt.getContent())));
        }
        return objects;
    }


    private  String formatTime(String time, int flag){
        time = time.replace("/", "-").replace("年", "-").replace("月", "-").replace("日", "");
//        if(time.length() < 18){
//            time += ":00";
//        }
        if(flag == 0){
            time = time + " 00:00:00";
        }else{
            time = time + " 23:59:59";
        }
        return time;
    }


    private static String formatContent(String source, String content){
//        if(StringUtils.equals(source, YD)){
//            return "<span class=\"source_tip_yd\">" + source + "</span>" + " " + content;
//        }else if(StringUtils.equals(source, JJ)){
//            return "<span class=\"source_tip_jj\">" + source + "</span>" + " " + content;
//        }else if(StringUtils.equals(source, WK)){
//            return "<span class=\"source_tip_wk\">" + source + "</span>" + " " + content;
//        }else if(StringUtils.equals(source, HLW)){
//            return "<span class=\"source_tip_hlw\">" + source + "</span>" + " " + content;
//        }else if(StringUtils.equals(source, WJ)){
//            return "<span class=\"source_tip_wj\">" + source + "</span>" + " " + content;
//        }else if(StringUtils.equals(source, BD)){
//            return "<span class=\"source_tip_bd\">" + source + "</span>" + " " + content;
//        }
//        return "<span class=\"source_tip_bd\">" + source + "</span>" + " " + content;
        return "【" + source + "】" + " " +content;
    }



    @SuppressWarnings("unused")
    private String getFwInfo(){
        String content = "";
        if(StringUtils.isNotBlank(fwly)){
            content += ("领域：" + fwly + "\n");
        }
        if(StringUtils.isNotBlank(fwjg)){
            content += ("发文机关：" + fwjg + "\n");
        }
        if(StringUtils.isNotBlank(extInfo)){
            content += (extInfo + "\n");
        }
        return content;
    }


    @RequestMapping(value = "/xcupload", method = RequestMethod.POST)
    @ResponseBody
    public JSONArray xcupload(MultipartFile[] files) {
        if(files != null) {
            JSONArray result = new JSONArray();
            String uploadFolder = getArteryDownloadTempFolder();
            for(MultipartFile file : files) {
                try {
                    if(!FileUtil.isValidFile(file, ".doc,.docx,.wps,.txt,.pdf")) {
                        logger.error("文件{}无效，忽略上传", file.getOriginalFilename());
                        continue;
                    }
                    String fileName  = FileUtil.getSanitizeFileName(file.getOriginalFilename());
                    fileName = fileName.replace("cknr_", "").replace("ckgs_", "");
                    File destFile = new File(uploadFolder, fileName);
                    FileUtils.writeByteArrayToFile(destFile, file.getBytes());
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("name", fileName);
                    jsonObject.put("path", destFile.getAbsolutePath());
                    jsonObject.put("type", file.getOriginalFilename().split("_")[0]);
                    result.add(jsonObject);

                } catch (Exception e) {
                    logger.error("保存文件失败！{}", file.getOriginalFilename(), e);
                }
            }
            return result;
        }
        return new JSONArray();
    }


    private String getArteryDownloadTempFolder() {
        String templatePath = FileUtils.getTempDirectoryPath();
        if (!templatePath.endsWith(File.separator)) {
            templatePath = templatePath + File.separator;
        }

        return templatePath + "artery" + File.separator + "downloads" + File.separator + UUIDHelper.getUuid();
    }

    /**
     * 生成时脚本(atyCheckboxshshs)
     *
     * @param component 控件对象
     */
    public void atyCheckboxshshsOnShow(Component component) {

        String corpId = sessionUtil.getCorpid();
        if(isCoCall){
            corpId = getCoCallCorpName();
        }
        boolean has = chatLinkService.containDwInfo(corpId);
//        component.setProperty("show", has);
        component.setProperty("hidden", !has);
        if(has){
            //从数据库获取数据
            String loginId = getDecryptUserId(sessionUtil.getUserid());
            UserData userData = new UserData(sessionUtil.getCorpid(), loginId, SELECTDWXX);
            UserData selectUserData = userDataMapper.selectOne(userData);
            if(selectUserData != null){
                component.setProperty("value", StringUtils.equals(selectUserData.getValue(), SELECTDWXX_YES)? "yes": "");
            }
            component.getParentComponent().findComponentById("atytooltipIconfont")
                    .setProperty("content", chatLinkService.getDwInfo(isCoCall? getCoCallCorpName():corpId, industryName, getCoCallCorpName()));
        }else{
            component.getParentComponent().findComponentById("atytooltipIconfont").setProperty("show", "false");
        }
    }



    @RequestMapping(value = "/updateDwpz", method = RequestMethod.POST)
    @ResponseBody
    public String updateDwpz(String value) {
        String corpId = sessionUtil.getCorpid();
        String loginId = getDecryptUserId(sessionUtil.getUserid());

        UserData userDatas = userDataMapper.selectOne(new UserData(corpId, loginId, SELECTDWXX));
        if(userDatas == null){
            //插入
            userDataMapper.insert(new UserData(UUIDHelper.getUuid(), corpId, loginId, SELECTDWXX, value, new Date()));
        }else{
            //更新
            userDatas.setValue(value);
            userDataMapper.updateByPrimaryKey(userDatas);
        }
        return "ok";
    }



    public String getCoCallCorpName(){
        if(isCoCall){
            if(securityService.getCurrUserInfo() != null && securityService.getCurrUserInfo().getExt() != null){
                return (String) securityService.getCurrUserInfo().getExt().get("corpName");
            }
        }
        return "";
    }


    /**
     * 生成时脚本(atyContainerZwufy)
     *
     * @param component 控件对象
     */
    public void atyContainerZwufyOnShow(Component component) {
        String s = llmConfigService.selectValueByKey("chat.llmparams.showWordControlBtn");
        boolean showWordControlBtn = s == null || Boolean.parseBoolean(s);
        component.setProperty("hidden", !showWordControlBtn);
    }

    /**
     * 点击时脚本(atyButtonGkdoi2)
     *
     * @return
     */
    @RequestMapping("/atyButtonGkdoi2/onClickServer")
    @ResponseBody
    public String atyButtonGkdoi2OnClickServer() {
        String ckgsPath = ArteryRequestUtil.getParamString("ckgs_path");
        String rightTxt = ArteryRequestUtil.getParamString("right_txt");
        String leftTxt = "";
        if(StringUtils.isBlank(ckgsPath)){
            leftTxt = ArteryRequestUtil.getParamString("ckgs_text");
        }else{
            leftTxt = ExtractTxtUtil.extractTxtFromFile(ckgsPath);
        }
        String id = UUIDHelper.getUuid();
        Map<String, String> compareTxt = new HashMap<>();
        compareTxt.put("left", leftTxt);
        compareTxt.put("right", rightTxt);
        compareCache.add(id, compareTxt);
        return id;
    }


    @RequestMapping(value = "/getCacheData", method = RequestMethod.POST)
    @ResponseBody
    public Object getCacheData(String key) {
        Map<String, String> data = compareCache.get(key);
        compareCache.delete(key);
        return data;
    }

    /**
     * 数据源查询方法
     * 收藏的数据源
     * @param qp 查询参数，只在分页查询时可用，否则为null
     * @return Object 当为分页查询时，需要返回IPagableData对象，否则可为任意pojo
     */
    public Object dsRs1(IQueryInfo qp) {
        String keyword = getParams("keyword", "");
        JSONObject userInfo = getParamUserInfo();
        String userId = userInfo.getString("userId");
        if(org.apache.commons.lang3.StringUtils.isNotBlank(userId)){
            userId = getDecryptUserId(userId);
        }else {
            userId = sessionUtil.getUserid();
        }
        int offset = qp.getOffset();
        int limit = qp.getLimit();

        userId = getDecryptUserId(userId);
        ArteryPageableData data = new ArteryPageableData<>();

        if(org.apache.commons.lang3.StringUtils.isBlank(userId)) {
            data.setData(new ArrayList<>());
            data.setTotal(0);
        }else {
            try {
//                QueryData queryData = fwService.search(keyword, "", "", userId, offset, limit, true);
                String dataType = "";
                if (StringUtils.equals(sysConfigService.getConfig("sys.whdw.enable"), "true")) {
                    dataType = "ckjg";
                }
                ArteryPageableData<List<JSONObject>> queryData = policyService.favoriteList(offset, limit, userId, keyword, dataType, "", true);

                if(queryData!=null&&queryData.getData()!=null){
                    setExtraInfo(queryData.getData());
                }


                data.setData(queryData.getData());
                data.setTotal(queryData.getTotal());
                data.setOffset(offset);
                data.setLimit(limit);
            }catch (Exception e){
                data.setLimit(limit);
                data.setData(new ArrayList<>());
                data.setOffset(offset);
                data.setTotal(0);
            }
        }
        return data;
    }

    private void setExtraInfo(List<JSONObject> result) {
        String ssoLoginString =  "&tenantId=" + sessionUtil.getTenantId() + "&" + Constant.PARAM_TRANSFTER_TOKEN_VKT + "=" + sessionUtil.getToken()
                + "&" + Constant.PARAM_TRANSFTER_USERNAME_UCP + "=" + URLEncoder.encode(sessionUtil.getLoginId()) + "&" + Constant.PARAM_TRANSFTER_PASSWORD_IQT + "=" + URLEncoder.encode(sessionUtil.getPassword());
        String requestServerAddr = "";
        try {
            requestServerAddr = new URL(ObjectUtils.toString(ArteryRequestUtil.getRequest().getRequestURL())).getHost();
        } catch (MalformedURLException e) {
            logger.error("请求URL转化URL对象异常");
        }
        String detailUrl = ZxBaseService.getOuterGwzkUrl("",requestServerAddr);
        if(!detailUrl.endsWith("/")) {
            detailUrl = detailUrl + "/";
        }
        if(cocallLoginEnabled){
            ssoLoginString = ssoLoginString + "&ksp_userId=" + URLEncoder.encode(sessionUtil.getLoginId());
        }


        for(JSONObject item : result) {
            String _index = item.getString("_index");
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_OD, _index)) {
                item.put("label", "公文");
                item.put("labelsimp", "公");
                item.put("detailUrl", detailUrl+"gwk/gwkxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_FLFG, _index)) {
                item.put("label", "法律法规");
                item.put("labelsimp", "法");
                item.put("detailUrl", detailUrl+"flfg/flfgxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_JH, _index)) {
                item.put("label", "学习新时代");
                item.put("labelsimp", "学");
                item.put("detailUrl", detailUrl+"ldjh/ldjhxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_NEWS, _index)) {
                item.put("label", "政务热点");
                item.put("labelsimp", "政");
                item.put("detailUrl", detailUrl+"zwrd/zwrdxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_JD, _index)) {
                item.put("label", "军事热点");
                item.put("labelsimp", "军");
                item.put("detailUrl", detailUrl+"jssj/jssjxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WIKI, _index) || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WIKI2, _index) || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WIKIGOV, _index)) {
                item.put("label", "政务百科");
                item.put("labelsimp", "政");
                item.put("detailUrl", detailUrl+"zwbk/zwbkxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_FW, _index) || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_FW_UNIT, _index)) {
                item.put("labelsimp", "范");
                item.put("label", "范文");
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WDZS, _index)) {
                item.put("labelsimp", "我");
                item.put("label", "我的知识");
                item.put("detailUrl", detailUrl+"wdzs/wdzsxq?id="+item.getString("id")+ssoLoginString);
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_ZCDT, _index)) {
                item.put("labelsimp", "策");
                item.put("label", "政策动态");
                item.put("detailUrl", "zcdt");
            }
            if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_SNPT, _index)) {
                item.put("labelsimp", "使");
                item.put("label", "使能知识库");
                item.put("detailUrl", "snpt");
            }
            if(!item.containsKey("label")) {
                item.put("labelsimp", "知");
                item.put("label", "知识库");
                String indexName = getIndexName(_index);
                Map dataModule = dataModuleService.getModuleByIndex(indexName);
                if(dataModule != null) {
                    item.put("detailUrl", detailUrl+"custom/dyn/module/modulexq?id="+item.getString("id")+"&index="+ MapUtils.getString(dataModule, "index")+"&index_name="+MapUtils.getString(dataModule, "name")+ssoLoginString);
                }
            }
        }
    }
    private String getIndexName(String _index) {
        if(org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_OD, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_FLFG, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_JH, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_NEWS, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_JD, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WIKI, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WIKI2, _index)
                || org.apache.commons.lang3.StringUtils.equals(ZxConst.INDEX_WIKIGOV, _index)) {
            return ZxConst.INDEX_OD;
        }
        return _index;
    }

    private ChatParams buildChatParams(JSONObject params, UserInfo userInfo) {
        ChatParams chatParams = new ChatParams();
        if(params.containsKey("type")) {
            String type = params.getString("type");
            if(type.contains(ConstV2.ZY)){
                type = ConstV2.ZY;
            }
            if(type.contains(ConstV2.DG)){
                type = ConstV2.DG;
            }
            if(type.contains(ConstV2.QW)){
                type = ConstV2.QW;
            }
            chatParams.setType(type);
        }
        if(params.containsKey("wordCount")){
            chatParams.setArticleLength(params.getInteger("wordCount"));
        }
        if(params.containsKey("title")) {
            String title = params.getString("title");
            chatParams.setTitle(title);
        }
        if(params.containsKey("gwwz")) {
            chatParams.setGwwz(params.getString("gwwz"));
        }
        if(params.containsKey("cause")){
            chatParams.setCause(params.getString("cause"));
        }
        if(params.containsKey("abstract")){
            chatParams.setSummary(params.getString("abstract"));
        }
        if(params.containsKey("outline")){
            chatParams.setOutline(params.getString("outline"));
        }
        if(params.containsKey("isUseDwxx")){
            if(params.getBooleanValue("isUseDwxx")){
                chatParams.setDwInfo(chatLinkService.getDwInfo(isCoCall? getCoCallCorpName() : userInfo.getCorpid(), chatParams.getType(), industryName, getCoCallCorpName()));
            }
        }
        if(params.containsKey("zhailuids") && StringUtils.isNotBlank(params.getString("zhailuids"))){
            String zhailu = getZhailuInfo(userInfo.getLoginId() ,params.getString("zhailuids"));
            chatParams.setZhailu(zhailu);
        }
        if(params.containsKey("luyinids") && StringUtils.isNotBlank(params.getString("luyinids"))){
            String luyin = getLuyinInfo(params.getString("luyinids"));
            chatParams.setLuyin(luyin);
        }
        if(params.containsKey("shoucangids") && StringUtils.isNotBlank(params.getString("shoucangids"))){
            chatParams.setFavoirtes(getShoucangInfos(userInfo.getLoginId() ,params.getString("shoucangids")));
        }
        if(params.containsKey("filepaths")){
            chatParams.setImitative(ExtractTxtUtil.extractTxtFromFolder(params.getJSONArray("filepaths"), "ckgs_"));
            chatParams.setReferences(ExtractTxtUtil.extractTxtsFromFolder(params.getJSONArray("filepaths"), "cknr_"));
            chatParams.setExcelReferencePath(ExtractTxtUtil.getExcelReferencePath(params.getJSONArray("filepaths"), "cknr_"));
            if(chatParams.getExcelReferencePath().size() > 0){
                chatParams.addReferences(" ");
            }
        }
        if(params.containsKey("fwckContent") && StringUtils.isNotBlank(params.getString("fwckContent"))){
            chatParams.setImitative(ExtractTxtUtil.extractTxtFromText(params.getString("fwckContent")));
        }
        if(params.containsKey("nrckContent") && StringUtils.isNotBlank(params.getString("nrckContent"))){
            List<String> refContents = new ArrayList<>();
            refContents.add(ExtractTxtUtil.extractTxtFromText(params.getString("nrckContent")));
            chatParams.setReferences(refContents);
        }
        if(params.containsKey("exchange")){
            chatParams.setExchange(params.getBooleanValue("exchange"));
        }
        if(params.containsKey("isWps")){
            chatParams.setWps(params.getBooleanValue("isWps"));
        }
        if(params.containsKey("llmMode")){
            chatParams.setUseThink(useThink(params.getBooleanValue("llmMode")));
        }
        chatParams.initGwwz(promptService.getSuppotGwwz());
        // 检测有没有新型模板
        if(params.containsKey("tplContent")) {
            String tplContent = params.getString("tplContent");
            if(StringUtils.isNotBlank(tplContent)) {
                chatParams.setTemplate(tplContent);
            }
        }
        if(StringUtils.isBlank(chatParams.getTemplate()) && StringUtils.isNotBlank(commonPromptService.getTemplate(chatParams))){
            chatParams.setTemplate(commonPromptService.getTemplate(chatParams));
        }

        // 判断是否存在自定义的角色信息
        UserCustomInfo userCustomInfo = getSelectedRoleInfo();
        if(userCustomInfo != null && userCustomInfo.getSelectUserData() != null && StringUtils.isNotBlank(userCustomInfo.getSelectUserData().getValue())){
            chatParams.setCustomSystemRole(userCustomInfo.getSelectUserData().getValue());
        }

        if(params.containsKey("needSearch")){
            chatParams.setNeedSearch(params.getBooleanValue("needSearch"));
        }
        if(params.containsKey("needFineSearch")){
            chatParams.setNeedFineSearch(params.getBooleanValue("needFineSearch"));
        }
        if(params.containsKey("evaluationInfo") && StringUtils.isNotBlank(params.getString("evaluationInfo"))){
            chatParams.setEvaluationInfo(params.getString("evaluationInfo"));
        }
        if(params.containsKey("ragZskIndex")){
            logger.info("ragZskIndex: {}", params.getString("ragZskIndex"));
            chatParams.setClassifyList(params.getString("ragZskIndex"));
        }
        return chatParams;



    }

    /**
     * 生成时脚本(atyContainerCzgel)
     *
     * @param component 控件对象
     */
    public void atyContainerCzgelOnShow(Component component) {
        component.setProperty("show", !StringUtils.equals(Const.SYS_TYPE_HDGA, systype));
    }

    /**
     * 生成时脚本(atyContainerWrnrw)
     *
     * @param component 控件对象
     */
    public void atyContainerWrnrwOnShow(Component component) {
        component.setProperty("show", !StringUtils.equals(Const.SYS_TYPE_HDGA, systype));
    }

    /**
     * 生成时脚本(atyContainerKlqld)
     *
     * @param component 控件对象
     */
    public void atyContainerKlqldOnShow(Component component) {
        component.setProperty("show", !StringUtils.equals(Const.SYS_TYPE_HDGA, systype));
    }

    /**
     * 生成时脚本(atyContainerBvxzh)
     *
     * @param component 控件对象
     */
    public void atyContainerBvxzhOnShow(Component component) {
        component.setProperty("show", !StringUtils.equals(Const.SYS_TYPE_HDGA, systype));
    }

    /**
     * 生成时脚本()
     * 展示用户自定义的角色信息
     * @param component 控件对象
     */
    public void RoleInfoShow(Component component) {
        String defaultRole = commonPromptService.getDefaultQcRoleInfo();
        UserCustomInfo userCustomInfo = getSelectedRoleInfo();
        if(userCustomInfo != null && userCustomInfo.getSelectUserData() != null){
            String role = userCustomInfo.getSelectUserData().getValue();
            if(StringUtils.isBlank(role)){
                component.setProperty("value", defaultRole);
            }else{
                component.setProperty("value", role);
            }
        }else{
            component.setProperty("value", defaultRole);
        }
    }

    /**
     * 点击确认按钮时脚本(atyModelRole)
     * @return
     */
    @RequestMapping("/atyModelRole/onOkServer")
    @ResponseBody
    public boolean atyModelRoleOnOkServer() {
        try {
            String roleCotnet = ArteryRequestUtil.getParamString("roleContent");
            UserCustomInfo userCustomInfo = getSelectedRoleInfo();

            if(userCustomInfo != null && userCustomInfo.getSelectUserData() == null){
                UserData userData = userCustomInfo.getUserData();
                userData.setGxsj(new Date());
                userData.setValue(roleCotnet);
                userData.setId(UUIDHelper.getUuid());
                userDataMapper.insert(userData);
            }else{
                UserData selectUserData = userCustomInfo.getSelectUserData();
                selectUserData.setGxsj(new Date());
                selectUserData.setValue(roleCotnet);
                userDataMapper.updateByPrimaryKey(selectUserData);
            }
            logger.info("开始保存结果信息, 保存角色信息完成");
        }catch (Exception e){

        }

        return false;
    }


    private UserCustomInfo getSelectedRoleInfo(){
        try{
            String loginId = getDecryptUserId(sessionUtil.getUserid());
            UserData userData = new UserData(sessionUtil.getCorpid(), loginId, DATATYPE_ROLENAME);
            List<UserData> userData1 = userDataMapper.select(userData);
            logger.info("用户自定义数据查出来的条数是{}", userData1.size());
            if(userData1 == null || userData1.size() == 0){
                return new UserCustomInfo(userData, new UserData());
            }
            return new UserCustomInfo(userData, userData1.get(0));
        }catch (Exception e){
            logger.error("获取自定义的角色信息失败");
        }
        return null;
    }


    /**
     * 获取到搜索内容
     * @return
     */
    @RequestMapping(value = "/getSearchContent", method = RequestMethod.POST)
    @ResponseBody
    public Object getSearchContent(HttpServletRequest request, HttpServletResponse response) {
        List<DifySearchRes> res = new ArrayList<>();
        try {
            String requestBody = IOUtils.toString(request.getInputStream(), "UTF-8");
            JSONObject params = JSONObject.parseObject(requestBody);
            boolean checkBenefit = !StringUtils.equals("true", params.getString("simple"));
            UserInfo userInfo = buildUserInfo(params, false);

            if (checkBenefit && !checkBenefitService.check(Const.FUNC_ZNXZ, userInfo.getTenantId(), userInfo.getCorpid(), userInfo.getLoginId())) {
                logger.error("检索内容失败，没有权限");
                return res;
            }
            ChatParams chatParams = buildChatParams(params, userInfo);
            // 开始检索
            logger.info("开始检索");
            res = searchService.search(chatParams);
        }catch (Exception e){
            logger.error("【分段起草】权益校验失败", e);
        }
        return res;
    }

    /**
     * 生成时脚本(atyButtonUxpqc)
     *
     * @param component 控件对象
     */
    public void atyButtonUxpqcOnShow(Component component) {
        if(StringUtils.equals(llmConfigService.selectValueByKey("chat.dify.enable"), "false") && StringUtils.equals(llmConfigService.selectValueByKey("chat.snpt.enable"), "false")){
            component.setProperty("show", "false");
        }else{
            // 默认是true， 检查一下是否有dify的配置
            if(StringUtils.equals(llmConfigService.selectValueByKey("chat.show.searchbtn"),"false")){
                component.setProperty("show", "false");
            }
        }
    }

    /**
     * 数据源查询方法
     *
     * @param qp 查询参数，只在分页查询时可用，否则为null
     * @return Object 当为分页查询时，需要返回IPagableData对象，否则可为任意pojo
     */
    public Object dsRs2(IQueryInfo qp) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<DifySearchRes> data = null;
        try {
            String requestBody = ArteryRequestUtil.getParamString("params");
            if(StringUtils.isNotBlank(requestBody)) {
                JSONObject params = JSONObject.parseObject(requestBody);
                boolean checkBenefit = !StringUtils.equals("true", params.getString("simple"));
                UserInfo userInfo = buildUserInfo(params, false);
                if (checkBenefit && !checkBenefitService.check(Const.FUNC_ZNXZ, userInfo.getTenantId(), userInfo.getCorpid(), userInfo.getLoginId())) {
                    logger.error("检索内容失败，没有权限");
                    return result;
                }
                ChatParams chatParams = buildChatParams(params, userInfo);
                // 开始检索
                logger.info("开始检索");
                data = searchService.search(chatParams);
            }
        }catch (Exception e){
            logger.error("【分段起草】权益校验失败", e);
        }
        if(data != null && data.size() > 0) {
            String uuid = UUIDHelper.getUuid();
            _dify_search_data.put(uuid, data);
            Map<String, Object> map = new HashMap<>();
            map.put("total", data.size());
            map.put("data", data);
            map.put("uuid", uuid);
            result.add(map);
        }
        return result;
    }

    /**
     * 生成时脚本(atyContainerEtrak)
     *
     * @param component 控件对象
     */
    public void atyContainerEtrakOnShow(Component component) {
        String type = ArteryRequestUtil.getParamString("type");
        if(StringUtils.isNotBlank(type) && StringUtils.equals(type, "zg")){
            component.setProperty("show", "false");
        } else {
            if(StringUtils.equals(llmConfigService.selectValueByKey("chat.dify.enable"), "false") && StringUtils.equals(llmConfigService.selectValueByKey("chat.snpt.enable"), "false")){
                component.setProperty("show", "false");
            }else{
                // 默认是true， 检查一下是否有dify的配置
                if(StringUtils.equals(llmConfigService.selectValueByKey("chat.show.searchbtn"),"false")){
                    component.setProperty("show", "false");
                }
            }
        }
    }

    /**
     * 生成时脚本(atyContainerKervu)
     *
     * @param component 控件对象
     */
    public void atyContainerKervuOnShow(Component component) {
        String type = ArteryRequestUtil.getParamString("type");
        if(StringUtils.isNotBlank(type) && StringUtils.equals(type, "zg")){
            component.setProperty("class-name", "container-nrck-xzwg container-nrck-xzwg2 sel");
        } else {
            if(StringUtils.equals(llmConfigService.selectValueByKey("chat.dify.enable"), "false") && StringUtils.equals(llmConfigService.selectValueByKey("chat.snpt.enable"), "false")){
                component.setProperty("class-name", "container-nrck-xzwg container-nrck-xzwg2 sel");
            }else{
                // 默认是true， 检查一下是否有dify的配置
                if(StringUtils.equals(llmConfigService.selectValueByKey("chat.show.searchbtn"),"false")){
                    component.setProperty("class-name", "container-nrck-xzwg container-nrck-xzwg2 sel");
                }
            }
        }
    }

    /**
     * 生成时脚本(atyIconKrivz1)
     *
     * @param component 控件对象
     */
    public void atyIconKrivz1downOnShow(Component component){
        String snptEnable = llmConfigService.selectValueByKey("chat.snpt.enable");
        if(StringUtils.equals(snptEnable, "true")){
            component.setProperty("show", false);
        }else{
            component.setProperty("show", true);
        }
    }


    /**
     * 生成时脚本(atyContainerQpucs)
     *
     * @param component 控件对象
     */
    public void atyContainerQpucsOnShow(Component component) {
        String type = ArteryRequestUtil.getParamString("type");
        if(StringUtils.isNotBlank(type) && StringUtils.equals(type, "zg")){
            component.setProperty("class-name", "container-nrck-xzwg-popup container-nrck-xzwg-popup2");
        } else {
            if(StringUtils.equals(llmConfigService.selectValueByKey("chat.dify.enable"), "false") && StringUtils.equals(llmConfigService.selectValueByKey("chat.snpt.enable"), "false")){
                component.setProperty("class-name", "container-nrck-xzwg-popup container-nrck-xzwg-popup2");
            }else{
                // 默认是true， 检查一下是否有dify的配置
                if(StringUtils.equals(llmConfigService.selectValueByKey("chat.show.searchbtn"),"false")){
                    component.setProperty("class-name", "container-nrck-xzwg-popup container-nrck-xzwg-popup2");
                }
            }
        }
    }


    /**
     * 生成时脚本(atyContainerQpucs)
     *
     * @param component 控件对象
     */
    public void atyContainerQpucshmdOnShow(Component component) {
        if(StringUtils.equals(llmConfigService.selectValueByKey("chat.dify.enable"), "false") && StringUtils.equals(llmConfigService.selectValueByKey("chat.snpt.enable"), "false")){
            component.setProperty("class-name", "container-nrck-xzwg-popup container-nrck-xzwg-popup2");
        }else{
            // 默认是true， 检查一下是否有dify的配置
            if(StringUtils.equals(llmConfigService.selectValueByKey("chat.show.searchbtn"),"false")){
                component.setProperty("class-name", "container-nrck-xzwg-popup container-nrck-xzwg-popup2");
            }
        }
    }


    /**
     * 数据源查询方法
     *
     * @param qp 查询参数，只在分页查询时可用，否则为null
     * @return Object 当为分页查询时，需要返回IPagableData对象，否则可为任意pojo
     */
    public ArteryPageableData<List<OptionData>> dsRs3(IQueryInfo qp) {
        List<OptionData> objects = new ArrayList<>();
        String type = ArteryRequestUtil.getParamString("type");
        String queryStr = ArteryRequestUtil.getParamString("query");
        String loginId = ArteryRequestUtil.getParamString("loginId");
        Boolean isWeb = ArteryRequestUtil.getParamBoolean("isWeb");
        if (StringUtils.isNotBlank(loginId)) {
            loginId = getDecryptUserId(loginId);
        }
        loginId = sessionUtil.getLoginId(loginId);
        // 获取HMD个人文稿
        if((isHmdRag() && StringUtils.equals(type, "hmd")) || isZzdRag()){
            int limit = qp.getLimit();
            int offset = qp.getOffset();
            int currentPage = (offset / limit) + 1;
            ArteryPageableData<List<OptionData>> result = new ArteryPageableData<>();
            JSONObject data = hmdPDocumentService.queryPersonalDocuments(queryStr, currentPage, limit, null, null, isHmdRag());
            JSONArray dataArray = data.getJSONArray("data");
            for(int i = 0; i < dataArray.size(); i++){
                JSONObject item = dataArray.getJSONObject(i);
                objects.add(new OptionData("hmd_" + item.getString("url"), TextUtils.removeSuffix(item.getString("originalName"))));
            }
            result.setData(objects);
            result.setTotal(data.getInteger("total"));
            result.setOffset(offset);
            result.setLimit(limit);
            return result;
        }
        if(StringUtils.isNotBlank(type)) {
            List<Excerpt> excerpts = new ArrayList<>();
            if(StringUtils.equals(type, "scnr")){
                //获取收藏的信息
                if(StringUtils.isNotBlank(queryStr)){
                    excerpts = favoriteInfoService.select(loginId, queryStr,isWeb);
                }else{
                    excerpts = favoriteInfoService.selectByDateDesc(loginId,isWeb);
                }
            }else{
                //这里调用 函数，把数据获取出来
                excerpts = excerptService.selectByKeywordDesc(loginId, queryStr, null);
            }
            for(Excerpt excerpt : excerpts){
                objects.add(new OptionData(excerpt.getId(), formatContent(excerpt.getSource(), excerpt.getContent())));
            }
        }
        String offset = ArteryRequestUtil.getParamString("offset");
        if(StringUtils.isNotBlank(offset)) {
            qp.setOffset(Integer.parseInt(offset));
        }
        return QueryManager.datas(qp, objects);


    }
    public Object dsRs31(IQueryInfo qp) {

        String keyword = ArteryRequestUtil.getParamString("query");
        JSONObject userInfo = getParamUserInfo();
        String userId = userInfo.getString("userId");
        if(org.apache.commons.lang3.StringUtils.isNotBlank(userId)){
            userId = getDecryptUserId(userId);
        }
        userId = sessionUtil.getLoginId(userId);
        Boolean isFavoriteSort = org.apache.commons.lang3.StringUtils.isBlank(ArteryRequestUtil.getParamString("isFavoriteSort")) ? true : ArteryRequestUtil.getParamBoolean("isFavoriteSort");

        String pageInfoStr = getParams("pageInfo", "{\"offset\":0,\"limit\":10}");
        @SuppressWarnings("unused")
        JSONObject pageInfo = JSONObject.parseObject(pageInfoStr);
        int offset =qp.getOffset();
        int limit = qp.getLimit();

        userId = getDecryptUserId(userId);
        ArteryPageableData data = new ArteryPageableData<>();
        XzscResult xzscResult = null;
        try {
            //Object obj = luceneServer.search(keyword, "", "", "", offset, limit, userId, isFavoriteSort);
            Object obj = xzscService.search(keyword, "", "", "", offset, limit, userId, isFavoriteSort);
            xzscResult = JSONObject.parseObject(JSONObject.toJSONString(obj), XzscResult.class);
        } catch (Exception e) {
            logger.error("搜索写作素材失败：{}", e.getMessage(), e);
            xzscResult = new XzscResult().setCommonInfo(-1, "搜索失败", "", keyword, "", offset, limit);
        }


        data.setData(xzscResult.getData());
        data.setTotal(xzscResult.getSize());

        // 设置下分页信息
        if(org.apache.commons.lang3.StringUtils.isNotBlank(pageInfoStr)) {
            data.setOffset(offset);
            data.setLimit(limit);
        }

        return data;
    }

    /**
     * 数据源查询方法
     *
     * @param qp 查询参数，只在分页查询时可用，否则为null
     * @return Object 当为分页查询时，需要返回IPagableData对象，否则可为任意pojo
     */
    public Object dsRs4(IQueryInfo qp) {
        List<TemplateInfo> templateInfoList = templateService.selectCustomByType();
        templateInfoList.forEach(templateInfo -> {
            if(StringUtils.isBlank(templateInfo.getTitle())) {
                templateInfo.setTitle(templateInfo.getSubType());
            }
        });
        return templateInfoList;
    }

    /**
     * 生成时脚本(atyContainerVdsfq)
     *
     * @param component 控件对象
     */
    public void atyContainerVdsfqOnShow(Component component) {
        int count = templateService.countCustomByType();
        if(count == 0) {
            component.setProperty("show", false);
        }
    }

    /**
     * 生成时脚本(atyContainerYonzk)
     *
     * @param component 控件对象
     */
    public void atyContainerYonzkOnShow(Component component) {
        int count = templateService.countCustomByType();
        if(count == 0) {
            component.setProperty("class-name", "container-jgk-fxmb container-jgk-fxmb2");
        }
    }

    /**
     * 生成时脚本(atyContainerGengj)
     *
     * @param component 控件对象
     */
    public void atyContainerGengjOnShow(Component component) {
        int count = templateService.countCustomByType();
        if(count == 0) {
            component.setProperty("class-name", "container-nrck-xzwg-popup container-nrck-xzwg-popup2");
        }
    }
    public void atyContainerGwzsOnShow(Component component){
        if(hmdGwbdHidden) {
            component.setProperty("show", false);
            return;
        }
        boolean show = true;
        ClientUIService clientUIService = (ClientUIService) ArterySpringContext.getBean("clientUIService");
        String corpid = sessionUtil.getTenantCode();
        if(org.apache.commons.lang3.StringUtils.isBlank(corpid)){
            corpid = sessionUtil.getCorpid();
        }
        String configstr = clientUIService.getUIConfigJson(corpid);
        if(org.apache.commons.lang3.StringUtils.isNotBlank(configstr)) {
            JSONArray configarray = JSONArray.parseArray(configstr);
            for(int i = 0; i < configarray.size(); i++) {
                JSONObject configobject = configarray.getJSONObject(i);
                if(org.apache.commons.lang3.StringUtils.equals("gwzs", configobject.getString("group"))) {
                    show = org.apache.commons.lang3.StringUtils.equals("1", configobject.getString("showtype"));
                    break;
                }
            }
        }
        component.setProperty("show", show);
    }


    public void atyContainerGwzshmdOnShow(Component component){
        if(hmdGwbdHidden) {
            component.setProperty("show", false);
            return;
        }
        boolean show = true;
        ClientUIService clientUIService = (ClientUIService) ArterySpringContext.getBean("clientUIService");
        String corpid = sessionUtil.getTenantCode();
        if(org.apache.commons.lang3.StringUtils.isBlank(corpid)){
            corpid = sessionUtil.getCorpid();
        }
        String configstr = clientUIService.getUIConfigJson(corpid);
        if(org.apache.commons.lang3.StringUtils.isNotBlank(configstr)) {
            JSONArray configarray = JSONArray.parseArray(configstr);
            for(int i = 0; i < configarray.size(); i++) {
                JSONObject configobject = configarray.getJSONObject(i);
                if(org.apache.commons.lang3.StringUtils.equals("gwzs", configobject.getString("group"))) {
                    show = org.apache.commons.lang3.StringUtils.equals("1", configobject.getString("showtype"));
                    break;
                }
            }
        }
        component.setProperty("show", show);
    }


    /**
     * 点击时脚本(eva1)
     *
     * @return
     */
    @RequestMapping("eva1/onClickServer")
    public void eva1OnClickServer(@RequestBody JSONObject params, HttpServletResponse response) {
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setHeader("Transfer-Encoding", "chunked");

        try (OutputStream outputStream = response.getOutputStream()) {

            // 1. 业务处理，流式输出中间内容
            EvallmResult evallmResult;
            if (StringUtils.isBlank(params.getString("title"))) {
                if (StringUtils.isNotBlank(params.getString("fullText"))) {
                    params.put("title", params.getString("fullText").split("\n")[0]);
                }
            }
            if (params.containsKey("checkedItems")) {
                evallmResult = evaService.ealuateArticel(
                    params.getString("fullText"),
                    params.getString("title"),
                    params.getJSONArray("checkedItems"),
                    outputStream // evaService内部可继续流式输出
                );
            } else {
                evallmResult = evaService.ealuateArticel(
                    params.getString("fullText"),
                    params.getString("title"),
                    outputStream
                );
            }
            evallmResult.setTitle(params.getString("title"));
            evallmResult.setArticle(params.getString("fullText"));

            // 3. 输出分隔符
            outputStream.write("\n---END---\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            // 4. 输出结构化JSON
            String json = com.alibaba.fastjson.JSONObject.toJSONString(evallmResult);
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception e) {
            logger.error("【评估文章】调用评估服务失败", e);
        }
    }

    /**
     * 生成时脚本(atyTextSlxsm)
     *
     * @param component 控件对象
     */
    public void atyTextSlxsmOnShow(Component component) {
        if(hmdGwbdHidden) {
            component.setProperty("text", "仿写材料");
        }
    }

    /**
     * 生成时脚本(atyTextAbmah)
     *
     * @param component 控件对象
     */
    public void atyTextAbmahOnShow(Component component) {
        if(hmdGwbdHidden) {
            component.setProperty("text", "参考内容");
        }
    }

    /**
     * 生成时脚本(atyIconKrivz1)
     *
     * @param component 控件对象
     */
    public void atyIconKrivz1OnShow(Component component) {
        logger.info("hmd ragEnabled: {}, zzdRag {}", isHmdRag(), isZzdRag());
        component.setProperty("show", isHmdRag() || isZzdRag());
    }



    @RequestMapping(value = "/audioInfo", method = RequestMethod.POST)
    @ResponseBody
    public JSONObject audioInfo() {
        JSONObject obj = new JSONObject();
        Audio audio = audioService.getAudioById(ArteryRequestUtil.getParamString("id"));
        String ext = audio.getExt();
        Date time = audio.getCreateTime();
        String formattedTime = "";
        if (time != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日");
            formattedTime = sdf.format(time);
        }else{
            formattedTime = new java.text.SimpleDateFormat("yyyy年MM月dd日").format(new Date());
        }
        obj.put("time", formattedTime);
        if(StringUtil.isNotBlank(ext)){
            JSONObject jsonObject = JSONObject.parseObject(ext);
            if(jsonObject.containsKey("nickName")){
                obj.put("title", StringUtils.trim(jsonObject.getString("nickName")));
            }
        }
        if(!obj.containsKey("title")){
            String fileName = audio.getName();
            if (fileName != null && fileName.lastIndexOf('.') > 0) {
                fileName = fileName.substring(0, fileName.lastIndexOf('.'));
            }
            obj.put("title", fileName);
        }

        return obj;
    }

    /**
     * 生成时脚本(atyContainerDzfpk)
     *
     * @param component 控件对象
     */
    public void atyContainerDzfpkOnShow(Component component) {
        component.setProperty("show", !isHmdRag() && !isZzdRag());
    }


    /**
     * 生成时脚本(atyContainerDzfpk)
     *
     * @param component 控件对象
     */
    public void atyContainerDzfpkhmdOnShow(Component component) {
        component.setProperty("show", isHmdRag() || isZzdRag());
    }

}
