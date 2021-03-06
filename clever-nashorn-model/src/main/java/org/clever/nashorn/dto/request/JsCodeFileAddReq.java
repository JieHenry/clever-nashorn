package org.clever.nashorn.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.clever.common.PatternConstant;
import org.clever.common.model.request.BaseRequest;
import org.clever.common.validation.ValidIntegerStatus;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * 作者：lizw <br/>
 * 创建时间：2019/08/27 13:03 <br/>
 */
@ApiModel("新增JS代码或文件夹")
@EqualsAndHashCode(callSuper = true)
@Data
public class JsCodeFileAddReq extends BaseRequest {

    @ApiModelProperty("业务类型")
    @NotBlank
    @Length(max = 127)
    private String bizType;

    @ApiModelProperty("代码分组")
    @NotBlank
    @Length(max = 127)
    private String groupName;

    @ApiModelProperty("数据类型：1-文件，2-文件夹")
    @NotNull
    @ValidIntegerStatus(value = {1, 2}, message = "1-文件，2-文件夹")
    private Integer nodeType;

    @ApiModelProperty("上级路径，以“/”号结尾")
    @NotBlank
    @Length(max = 255)
    private String filePath;

    @ApiModelProperty("文件或文件夹名称")
    @NotBlank
    @Pattern(regexp = PatternConstant.Name_Pattern + "{0,255}")
    private String name;

    @ApiModelProperty("脚本内容")
    private String jsCode;

    @ApiModelProperty("说明")
    @Length(max = 511)
    private String description;
}
