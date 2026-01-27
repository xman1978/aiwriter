package com.thunisoft.intelligenteditor.controller.client;

import com.alibaba.fastjson.JSONObject;
import com.thunisoft.intelligenteditor.controller.client.gxrs.GxRsParam;
import com.thunisoft.intelligenteditor.controller.client.gxrs.GxrsCommonService;
import com.thunisoft.intelligenteditor.controller.client.gxrs.TextProcessType;
import com.thunisoft.intelligenteditor.service.auditlog.IWenkuAuditLogService;
import com.thunisoft.intelligenteditor.service.benefit.CheckBenefitService;
import com.thunisoft.intelligenteditor.service.config.SysConfigService;
import com.thunisoft.intelligenteditor.util.ExtractTxtUtil;
import com.thunisoft.llm.domain.Answer;
import com.thunisoft.llm.domain.UserInfo;
import com.thunisoft.llm.service.ChatLlmParams;
import com.thunisoft.llm.service.ICallLlm;
import com.thunisoft.llm.service.impl.promptImpl.gxrs.GxRsPromptService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.thunisoft.llm.writeragent.CondenseWriter;
import com.thunisoft.llm.writeragent.utils.XCallLlm;

@Controller
@RequestMapping({"client/gxrss"})
public class GxrssController {
  private static Logger logger = LoggerFactory.getLogger(com.thunisoft.intelligenteditor.controller.client.GxrssController.class);
  
  @Autowired
  private GxrsCommonService gxrsCommonService;
  
  @Autowired
  private CheckBenefitService checkBenefitService;
  
  @Autowired
  private ICallLlm callLlm;
  
  @Autowired
  private XCallLlm xCallLlm;
  
  @Autowired
  private ChatLlmParams chatLlmParams;
  
  @Autowired
  private GxRsPromptService promptService;
  
  @Autowired
  IWenkuAuditLogService auditLogService;
  
  @Autowired
  SysConfigService sysConfigService;
  
  @GetMapping
  public String index(HttpServletRequest request) {
    return "client/gxrss";
  }
  
  @RequestMapping(value = {"/chat"}, method = {RequestMethod.POST})
  @ResponseBody
  public void methodA(HttpServletRequest request, HttpServletResponse response) {
    try {
      this.chatLlmParams.initParams();
      String requestBody = IOUtils.toString((InputStream)request.getInputStream(), StandardCharsets.UTF_8);
      JSONObject params = JSONObject.parseObject(requestBody);
      boolean checkBenefit = !StringUtils.equals("true", params.getString("simple"));
      callChat(params, response, false, null, checkBenefit);
    } catch (Exception e) {
      logger.error("解析参数失败！", e);
    } 
  }
  
  public void callChat(JSONObject params, HttpServletResponse response, boolean isOpenCall, String errorResponse, boolean checkBenefit) {
    UserInfo userInfo = this.gxrsCommonService.buildUserInfo(params, isOpenCall);
    try {
      if (checkBenefit && !this.checkBenefitService.check(Integer.valueOf(2), userInfo.getTenantId(), userInfo.getCorpid(), userInfo.getLoginId())) {
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setHeader("Transfer-Encoding", "chunked");
        String message = "权益错误：抱歉，您“智能写作”的额度已用尽！";
        response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
        return;
      } 
    } catch (Exception e) {
      logger.info("【改写润色对话】当前改写润色对话权益校验失败", e);
    } 
    GxRsParam gxRsParam = this.gxrsCommonService.buildGxRsParams(params);
    if (StringUtils.isBlank(gxRsParam.getSentence())) {
      logger.info("【改写润色】选择的文本不能为空");
      return;
    } 
    response.setHeader("Content-Type", "text/html; charset=UTF-8");
    response.setHeader("Transfer-Encoding", "chunked");
    try (ServletOutputStream servletOutputStream = response.getOutputStream()) {
      AtomicReference<String> res = new AtomicReference<>("");
      Answer answer = new Answer();
      JSONObject inputParams = this.promptService.buildGxRsPrompt(gxRsParam);
      if (params.containsKey("isSelected") && params.getBoolean("isSelected")) {
        if (isRecommend(gxRsParam)) {
          res.set(this.promptService.recomadTj(gxRsParam, inputParams, (OutputStream)servletOutputStream));
        } else {
          res.set(this.promptService.recomadQt(gxRsParam, inputParams, (OutputStream)servletOutputStream));
        } 
      } else {
        // xman: 精简/总结文章内容
        CondenseWriter condenseWriter = new CondenseWriter(this.xCallLlm, gxRsParam.isUseThink(), gxRsParam.isExchange());
        if (gxRsParam.getProcessType().equals(TextProcessType.COMPRESS_SUMMARY)) {
          res.set(condenseWriter.summarizeText(gxRsParam.getSentence(), (OutputStream)servletOutputStream));
        } else if (gxRsParam.getProcessType().equals(TextProcessType.COMPRESS_SIMPLIFY)) {
          res.set(condenseWriter.condenseText(gxRsParam.getSentence(), gxRsParam.getLengthSize(), (OutputStream)servletOutputStream));
        } else {
          throw new IllegalArgumentException("无效的精简/总结类型: " + gxRsParam.getProcessType().getValue());
        }
      }

      answer.setAnswer(res.get());
      answer.setQuestion(JSONObject.toJSONString(inputParams.getJSONArray("messages")));
      answer.setPrompt(JSONObject.toJSONString(inputParams.getJSONArray("messages")));
      this.gxrsCommonService.saveGxRecordInfo(answer, userInfo, gxRsParam);        
      Map<String, Object> ext = new HashMap<>();
      ext.put("answer", res.get());
      this.auditLogService.saveAuditLog("改写润色", gxRsParam.getProcessType().getValue(), gxRsParam.getSentence(), "成功", ext);
    } catch (Exception e) {
      logger.error("【改写润色】大模型润色失败", e);
      if (errorResponse != null)
        this.gxrsCommonService.writebackResponse(response, errorResponse); 
    } 
  }
  
  private boolean isRecommend(GxRsParam gxRsParam) {
    return (gxRsParam.getProcessType().equals(TextProcessType.RECOMMEND_GOLDEN) || gxRsParam
      .getProcessType().equals(TextProcessType.RECOMMEND_ALLUSION) || gxRsParam
      .getProcessType().equals(TextProcessType.RECOMMEND_TITLE));
  }
  
  @RequestMapping(value = {"/uploadFile"}, method = {RequestMethod.POST})
  @ResponseBody
  public JSONObject uploadFile(MultipartFile file) {
    JSONObject result = new JSONObject();
    File tempFile = null;
    try {
      if (file == null || file.isEmpty()) {
        result.put("success", Boolean.valueOf(false));
        return result;
      } 
      String originalFileName = file.getOriginalFilename();
      String fileExtension = "";
      if (originalFileName != null && originalFileName.contains("."))
        fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".")); 
      tempFile = File.createTempFile("upload_", fileExtension);
      try(FileOutputStream fos = new FileOutputStream(tempFile); 
          InputStream is = file.getInputStream()) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1)
          fos.write(buffer, 0, bytesRead); 
        fos.flush();
      } 
      String content = ExtractTxtUtil.extractTxtFromFile(tempFile);
      if (StringUtils.isNotBlank(content)) {
        result.put("success", Boolean.valueOf(true));
        result.put("content", content);
      } 
    } catch (Exception e) {
      logger.error("文件上传失败", e);
      result.put("success", Boolean.valueOf(false));
      result.put("message", "文件上传失败：" + e.getMessage());
    } finally {
      if (tempFile != null && tempFile.exists())
        try {
          tempFile.delete();
        } catch (Exception e) {
          logger.error("删除临时文件失败：{}", tempFile.getPath(), e);
        }  
    } 
    return result;
  }
}
