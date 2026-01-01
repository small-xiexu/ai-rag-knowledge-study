package com.xbk.xfg.dev.tech.api;

import com.xbk.xfg.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author xiexu
 * @date 2026/1/1 17:13
 */
public interface IRAGService {

    /**
     * 返回知识库标签列表
     *
     * @return
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传知识库相关文件
     *
     * @param ragTag
     * @param files
     * @return
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

}
