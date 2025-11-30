package com.thunisoft.intelligenteditor.util;

import com.alibaba.fastjson.JSONArray;
import com.aspose.words.Document;
import com.aspose.words.Field;
import com.aspose.words.FieldCollection;
import com.aspose.words.LoadOptions;
import com.thunisoft.artery.util.uuid.UUIDHelper;
import com.thunisoft.intelligenteditor.util.FileType;
import com.thunisoft.intelligenteditor.util.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class ExtractTxtUtil {
  private static final Logger logger = LoggerFactory.getLogger(com.thunisoft.intelligenteditor.util.ExtractTxtUtil.class);
  
  private static final String NAME = "【文字抽取工具】";
  
  private static final String REGEX_PAGENO = "(—[.\\s]*PAGE[.\\s]*\\d+[.\\s]*—)|((^|\r?\n)PAGE\\s*(\r?\n|$))";
  
  private static final String REGEX_MERGEFORMAT = "\\s*—\\s*PAGE[\\s\\\\*]*.*[\\s\\\\*]*MERGEFORMAT\\d*\\s*—\\s*";
  
  public static String extractTxtFromFile(MultipartFile file) {
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null) {
      logger.error("未获取到原始的文件名");
      return "";
    } 
    String fileExtension = "";
    int lastDotIndex = originalFilename.lastIndexOf('.');
    if (lastDotIndex > 0)
      fileExtension = originalFilename.substring(lastDotIndex); 
    File tempFile = null;
    try {
      tempFile = File.createTempFile(UUIDHelper.getUuid(), fileExtension);
      FileUtils.writeByteArrayToFile(tempFile, file.getBytes());
      return extractTxtFromFile(tempFile);
    } catch (Exception e) {
      logger.error("获取文件的内容失败", e);
    } finally {
      if (tempFile != null && tempFile.exists())
        if (tempFile.delete()) {
          logger.info("删除文件成功");
        } else {
          logger.error("删除文件失败");
        }  
    } 
    return "";
  }
  
  public static String extractTxtFromFile(File file) throws Exception {
    String reuslt = "";
    if (!file.exists())
      throw new Exception("抽取文件的文件不存在"); 
    FileType fileType = getType(file);
    boolean flag = false;
    if (fileType == null) {
      flag = true;
    } else if (StringUtils.equals(fileType.getValue(), FileType.XLS_DOC.getValue()) || StringUtils.equals(fileType.getValue(), FileType.ZIP.getValue()) || StringUtils.equals(fileType.getValue(), FileType.PDF.getValue())) {
      flag = true;
    } 
    if (!flag)
      return ""; 
    if (StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "txt")) {
      reuslt = FileUtil.readFileContent(file.getAbsolutePath(), StandardCharsets.UTF_8.toString());
    } else if (StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "doc") || StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "docx") || StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "wps")) {
      // xman: 优化标题提取和表格提取（表格转 markdown）
      // reuslt = extractTxtFromDoc(file);
      reuslt = WordToTxtExtractor.extractTxtFromDoc(file);
    } else if (StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "pdf")) {
      reuslt = extractTxtFromPdf(file);
    } else if (StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "xls") || StringUtils.endsWith(file.getName().toLowerCase(Locale.ROOT), "xlsx")) {
      // xman: 增加 Excel 表格提取
      // logger.info("文件是excel文件，不进行抽取");
      reuslt = ExcelToHtmlUtil.extractTxtFromExcel(file);
    } else {
      throw new Exception("不支持的文件格式,仅支持docx， doc, txt, pdf");
    } 
    reuslt = reuslt.replaceAll("[^(a-zA-Z0-9\\u4e00-\\u9fa5\\pP\\r\\n，。、《》？！@#￥%……&*（）)]", "").replaceAll("\\r?\\n\\r?\\n", "\n").replace("#", " ").replace("-", " ");
    return reuslt;
  }
  
  public static String extractTxtFromText(String reuslt) {
    if (StringUtils.isBlank(reuslt))
      return ""; 
    reuslt = reuslt.replaceAll("[^(a-zA-Z0-9\\u4e00-\\u9fa5\\pP\\r\\n，。、《》？！@#￥%……&*（）)]", "").replaceAll("\\r?\\n\\r?\\n", "\n").replaceAll("\\n+", "\n").replace("#", " ").replace("-", " ");
    return reuslt;
  }
  
  @Deprecated
  private static String extractTxtFromDoc(File file) {
    try (InputStream is = new FileInputStream(file)) {
      LoadOptions loadOptions = new LoadOptions();
      loadOptions.getLanguagePreferences().setDefaultEditingLanguage(2052);
      Document doc = new Document(is, loadOptions);
      doc.getRevisions().acceptAll();
      doc.getChildNodes(19, true).clear();
      doc.getChildNodes(18, true).clear();
      FieldCollection flieds = doc.getRange().getFields();
      for (Field fliend : flieds) {
        if (fliend.getType() == 88)
          fliend.remove(); 
      } 
      String text = doc.getText();
      if (StringUtils.isNotBlank(text)) {
        if (!text.contains("\n") && text.contains("\r")) {
          text = text.replace("\r", "\n");
          text = text.replace("\013", "");
          text = text.replaceAll("\007{1,}", "\n");
          text = text.replaceAll("\007", "");
          text = text.replaceAll("\023", "");
          text = text.replaceAll("\025", "");
          text = text.replaceAll("\024", "");
          text = text.replaceAll("\f", "");
          text = StringUtils.replace(text, "\007", "");
          text = text.replaceAll("(—[.\\s]*PAGE[.\\s]*\\d+[.\\s]*—)|((^|\r?\n)PAGE\\s*(\r?\n|$))", "");
          text = text.replaceAll("\\s*—\\s*PAGE[\\s\\\\*]*.*[\\s\\\\*]*MERGEFORMAT\\d*\\s*—\\s*", "");
          text = text.replaceAll("HYPERLINK\\s*\"[^\"]*\"[\\\\t\\s]*\"[^\"]*\"", "");
          text = text.replaceAll("HYPERLINK\\s*\"[^\"]*\"", "");
        } else if (!text.contains("\n") && !text.contains("\r")) {
          text = text.replace("\007\007", "\n");
        } 
        if (text.startsWith("\n"))
          text = text.replaceAll("^\\n{1,}", ""); 
        return text;
      } 
    } catch (Exception e) {
      logger.error("{}抽取文件失败，文件是{}", "【文字抽取工具】", file);
    } 
    return "";
  }
  
  private static String extractTxtFromPdf(File file) {
    try (InputStream is = new FileInputStream(file)) {
      PDDocument document = PDDocument.load(is);
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(document);
      if (StringUtils.isNotBlank(text)) {
        if (!text.contains("\n") && text.contains("\r")) {
          text = text.replace("\r", "\n");
          text = text.replace("\013", "");
          text = text.replaceAll("\007{1,}", "\n");
          text = text.replaceAll("\007", "");
          text = text.replaceAll("\023", "");
          text = text.replaceAll("\025", "");
          text = text.replaceAll("\024", "");
          text = text.replaceAll("\f", "");
          text = StringUtils.replace(text, "\007", "");
          text = text.replaceAll("(—[.\\s]*PAGE[.\\s]*\\d+[.\\s]*—)|((^|\r?\n)PAGE\\s*(\r?\n|$))", "");
          text = text.replaceAll("\\s*—\\s*PAGE[\\s\\\\*]*.*[\\s\\\\*]*MERGEFORMAT\\d*\\s*—\\s*", "");
          text = text.replaceAll("HYPERLINK\\s*\"[^\"]*\"[\\\\t\\s]*\"[^\"]*\"", "");
          text = text.replaceAll("HYPERLINK\\s*\"[^\"]*\"", "");
        } else if (!text.contains("\n") && !text.contains("\r")) {
          text = text.replace("\007\007", "\n");
        } 
        if (text.startsWith("\n"))
          text = text.replaceAll("^\\n{1,}", ""); 
        return text;
      } 
    } catch (Exception e) {
      logger.error("{}抽取文件失败，文件是{}", "【文字抽取工具】", file);
    } 
    return "";
  }
  
  public static void main(String[] args) throws Exception {
    File f = new File("D:\\document\\cocall\\filereceive\\师岳\\国家新闻出版广电总局关于给予新疆兵团卫视和四川卫视暂停商业广告播出处理的通报.docx");
    String r = extractTxtFromFile(f);
    System.out.println(r);
  }
  
  public static String extractTxtFromFolder(String filefolder) {
    File folder = new File(filefolder);
    StringBuilder content = new StringBuilder();
    if (folder.exists())
      for (File f : (File[])Objects.<File[]>requireNonNull(folder.listFiles())) {
        try {
          content.append(extractTxtFromFile(f));
          content.append("\n");
        } catch (Exception e) {
          logger.error("{}提取文本失败，提取的文件名称是{}", new Object[] { "【文字抽取工具】", f.getName(), e });
        } 
      }  
    return content.toString();
  }
  
  public static String extractTxtFromFolder(JSONArray files) {
    StringBuilder content = new StringBuilder();
    if (files != null && files.size() > 0)
      for (int i = 0; i < files.size(); i++) {
        String savepath = files.getString(i);
        if (savepath.contains("%2e") || savepath.contains("%00")) {
          logger.error("检测到编码特殊字符，拒绝访问 {}", savepath);
        } else {
          Path normalizedPath = Paths.get(savepath, new String[0]).normalize();
          if (normalizedPath.toString().contains("..") || normalizedPath.toString().contains("\000")) {
            logger.error("检测到非法路径字符，拒绝访问");
          } else {
            File f = new File(savepath);
            try {
              content.append(extractTxtFromFile(f));
              content.append("\n");
            } catch (Exception e) {
              logger.error("{}提取文本失败，提取的文件名称是{}", new Object[] { "【文字抽取工具】", f.getName(), e });
            } 
          } 
        } 
      }  
    return content.toString().trim();
  }
  
  public static String extractTxtFromFolder(JSONArray files, String type) {
    StringBuilder content = new StringBuilder();
    if (files != null && files.size() > 0)
      for (int i = 0; i < files.size(); i++) {
        String savepath = files.getString(i);
        if (StringUtils.startsWith(savepath, type)) {
          savepath = savepath.replace(type, "");
          if (savepath.contains("%2e") || savepath.contains("%00")) {
            logger.error("检测到编码特殊字符，拒绝访问 {}", savepath);
          } else {
            Path normalizedPath = Paths.get(savepath, new String[0]).normalize();
            if (normalizedPath.toString().contains("..") || normalizedPath.toString().contains("\000")) {
              logger.error("检测到非法路径字符，拒绝访问");
            } else {
              File f = new File(savepath);
              try {
                content.append(extractTxtFromFile(f));
                content.append("\n");
              } catch (Exception e) {
                logger.error("{}提取文本失败，提取的文件名称是{}", new Object[] { "【文字抽取工具】", f.getName(), e });
              } 
            } 
          } 
        } 
      }  
    return content.toString().trim();
  }
  
  public static List<String> extractTxtsFromFolder(JSONArray files, String type) {
    List<String> content = new ArrayList<>();
    if (files != null && files.size() > 0)
      for (int i = 0; i < files.size(); i++) {
        String savepath = files.getString(i);
        if (StringUtils.startsWith(savepath, type)) {
          savepath = savepath.replace(type, "");
          if (savepath.contains("%2e") || savepath.contains("%00")) {
            logger.error("检测到编码特殊字符，拒绝访问 {}", savepath);
          } else {
            Path normalizedPath = Paths.get(savepath, new String[0]).normalize();
            if (normalizedPath.toString().contains("..") || normalizedPath.toString().contains("\000")) {
              logger.error("检测到非法路径字符，拒绝访问");
            } else {
              File f = new File(savepath);
              try {
                content.add(extractTxtFromFile(f));
              } catch (Exception e) {
                logger.error("{}提取文本失败，提取的文件名称是{}", new Object[] { "【文字抽取工具】", f.getName(), e });
              } 
            } 
          } 
        } 
      }  
    return content;
  }
  
  public static String extractTxtFromFile(String savepath) {
    Path normalizedPath = Paths.get(savepath, new String[0]).normalize();
    if (normalizedPath.toString().contains("..") || normalizedPath.toString().contains("\000")) {
      logger.error("检测到非法路径字符，拒绝访问");
      return "";
    } 
    File f = new File(savepath);
    try {
      return extractTxtFromFile(f);
    } catch (Exception e) {
      logger.error("{}提取文本失败，提取的文件名称是{}", new Object[] { "【文字抽取工具】", f.getName(), e });
      return "";
    } 
  }
  
  public static FileType getType(File file) {
    try {
      String fileHead = getFileContent(new FileInputStream(file));
      if (fileHead == null || fileHead.length() == 0)
        return null; 
      fileHead = fileHead.toUpperCase();
      FileType[] fileTypes = FileType.values();
      for (FileType type : fileTypes) {
        if (fileHead.startsWith(type.getValue()))
          return type; 
      } 
    } catch (IOException e) {
      logger.error("通过文件获取流失败， 文件地址是{}", file.getAbsolutePath(), e);
    } 
    return null;
  }
  
  private static String getFileContent(InputStream inputStream) throws IOException {
    try (InputStream in = inputStream) {
      byte[] b = new byte[28];
      in.read(b, 0, 28);
      return bytesToHexString(b);
    } 
  }
  
  private static String bytesToHexString(byte[] src) {
    StringBuilder stringBuilder = new StringBuilder();
    if (src == null || src.length <= 0)
      return null; 
    for (int i = 0; i < src.length; i++) {
      int v = src[i] & 0xFF;
      String hv = Integer.toHexString(v);
      if (hv.length() < 2)
        stringBuilder.append(0); 
      stringBuilder.append(hv);
    } 
    return stringBuilder.toString();
  }
  
  public static List<String> getExcelReferencePath(JSONArray jsonArray, String type) {
    List<String> excelReferencePath = new ArrayList<>();
    if (jsonArray != null && jsonArray.size() > 0)
      for (int i = 0; i < jsonArray.size(); i++) {
        String savepath = jsonArray.getString(i);
        if (StringUtils.startsWith(savepath, type) && (
          StringUtils.endsWithIgnoreCase(savepath, ".xls") || StringUtils.endsWithIgnoreCase(savepath, ".xlsx"))) {
          savepath = savepath.replace(type, "");
          excelReferencePath.add(savepath);
        } 
      }  
    return excelReferencePath;
  }
}
