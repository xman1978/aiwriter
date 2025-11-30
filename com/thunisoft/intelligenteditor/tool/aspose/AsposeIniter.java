package com.thunisoft.intelligenteditor.tool.aspose;

import java.io.ByteArrayInputStream;
import org.apache.poi.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class AsposeIniter {
  private static final Logger log = LoggerFactory.getLogger(com.thunisoft.intelligenteditor.tool.aspose.AsposeIniter.class);
  
  public AsposeIniter() {
    System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
    byte[] licenseBytes = getResourceFileData("aspose/license.xml");
    if (licenseBytes == null) {
      log.error("未获取到 Aspose 的 license.xml，请检查!");
      return;
    } 
    try {
      (new com.aspose.words.License()).setLicense(new ByteArrayInputStream(licenseBytes));
      (new com.aspose.cells.License()).setLicense(new ByteArrayInputStream(licenseBytes));
    } catch (Exception e) {
      log.error("init License 异常!", e);
    } 
  }
  
  private byte[] getResourceFileData(String path) {
    try {
      ClassPathResource classPathResource = new ClassPathResource(path);
      if (classPathResource.exists())
        return IOUtils.toByteArray(classPathResource.getInputStream()); 
    } catch (Exception e) {
      log.error("读取文件时出错", e);
    } 
    return null;
  }
}
