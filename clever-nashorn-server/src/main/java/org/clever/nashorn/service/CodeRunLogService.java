package org.clever.nashorn.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.commons.lang3.StringUtils;
import org.clever.common.model.request.QueryBySort;
import org.clever.nashorn.dto.request.CodeRunLogQueryReq;
import org.clever.nashorn.dto.response.CodeRunLogQueryRes;
import org.clever.nashorn.dto.response.CodeRunLogStatusSummaryRes;
import org.clever.nashorn.entity.CodeRunLog;
import org.clever.nashorn.entity.EnumConstant;
import org.clever.nashorn.entity.JsCodeFile;
import org.clever.nashorn.mapper.CodeRunLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 作者：lizw <br/>
 * 创建时间：2019/08/27 09:34 <br/>
 */
@Transactional(readOnly = true)
@Service
public class CodeRunLogService {
    @Autowired
    private CodeRunLogMapper codeRunLogMapper;

    public CodeRunLog getCodeRunLog(Long id) {
        return codeRunLogMapper.selectById(id);
    }

    public IPage<CodeRunLogQueryRes> queryByPage(CodeRunLogQueryReq query) {
        query.addOrderFieldMapping("id", "a.id");
        query.addOrderFieldMapping("jsCodeId", "a.js_code_id");
        query.addOrderFieldMapping("runStart", "a.run_start");
        query.addOrderFieldMapping("runEnd", "a.run_end");
        query.addOrderFieldMapping("status", "a.status");
        query.addOrderFieldMapping("createAt", "a.create_at");
        query.addOrderFieldMapping("updateAt", "a.update_at");
        query.addOrderFieldMapping("bizType", "b.biz_type");
        query.addOrderFieldMapping("groupName", "b.group_name");
        query.addOrderFieldMapping("filePath", "b.file_path");
        query.addOrderFieldMapping("name", "b.name");
        query.addOrderFieldMapping("runTime", "b.name");
        if (query.getOrderFields().size() <= 0) {
            query.addOrderField("createAt", QueryBySort.DESC);
        }
        return query.result(codeRunLogMapper.queryByPage(query));
    }

    public List<CodeRunLogStatusSummaryRes> groupByStatus(CodeRunLogQueryReq query) {
        query.setSearchCount(false);
        return codeRunLogMapper.groupByStatus(query);
    }

    @Transactional
    public Long startLog(JsCodeFile jsCodeFile) {
        CodeRunLog codeRunLog = new CodeRunLog();
        codeRunLog.setJsCodeId(jsCodeFile.getId());
        codeRunLog.setJsCode(jsCodeFile.getJsCode());
        codeRunLog.setRunStart(new Date());
        codeRunLog.setRunLog(StringUtils.EMPTY);
        codeRunLog.setStatus(EnumConstant.Status_1);
        codeRunLogMapper.insert(codeRunLog);
        return codeRunLog.getId();
    }

    @Transactional
    public void endLog(Long codeRunLogId, Integer status) {
        CodeRunLog codeRunLog = new CodeRunLog();
        codeRunLog.setId(codeRunLogId);
        codeRunLog.setRunEnd(new Date());
        codeRunLog.setStatus(status);
        codeRunLogMapper.updateById(codeRunLog);
    }

    @Transactional
    public void appendLog(Long codeRunLogId, String addLog) {
        codeRunLogMapper.appendLog(codeRunLogId, addLog);
    }
}
