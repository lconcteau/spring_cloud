package top.tangyh.lamp.msg.controller;


import cn.afterturn.easypoi.entity.vo.NormalExcelConstants;
import cn.afterturn.easypoi.excel.ExcelExportUtil;
import cn.afterturn.easypoi.excel.ExcelXorHtmlUtil;
import cn.afterturn.easypoi.excel.entity.ExcelToHtmlParams;
import cn.afterturn.easypoi.excel.entity.ExportParams;
import cn.afterturn.easypoi.excel.entity.enmus.ExcelType;
import cn.afterturn.easypoi.view.PoiBaseView;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.tangyh.basic.annotation.log.SysLog;
import top.tangyh.basic.annotation.security.PreAuth;
import top.tangyh.basic.base.R;
import top.tangyh.basic.base.request.PageParams;
import top.tangyh.basic.base.request.PageUtil;
import top.tangyh.basic.context.ContextUtil;
import top.tangyh.basic.database.mybatis.conditions.Wraps;
import top.tangyh.basic.database.mybatis.conditions.query.LbqWrapper;
import top.tangyh.lamp.authority.api.UserBizApi;
import top.tangyh.lamp.msg.dto.MsgPageResult;
import top.tangyh.lamp.msg.dto.MsgQuery;
import top.tangyh.lamp.msg.dto.MsgSaveDTO;
import top.tangyh.lamp.msg.entity.Msg;
import top.tangyh.lamp.msg.enumeration.MsgType;
import top.tangyh.lamp.msg.service.MsgService;
import top.tangyh.lamp.oauth.api.RoleApi;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * ???????????????
 * ????????????
 * </p>
 *
 * @author zuihou
 * @date 2019-08-01
 */
@Slf4j
@RestController
@RequestMapping("/msg")
@Api(value = "MsgController", tags = "????????????")
@Validated
@PreAuth(replace = "msg:msg:")
@RequiredArgsConstructor
public class MsgController {
    private final MsgService msgService;
    private final RoleApi roleApi;
    private final UserBizApi userBizApi;


    /**
     * ???????????????????????? ??????
     * WAIT:??????
     * NOTIFY:??????;
     * WARN:??????;
     * ????????? msg_receive??????????????????????????????????????????
     * ????????? msg_receive??????????????????????????????????????????
     * <p>
     * PUBLICITY:????????????;  ?????????????????????
     * ?????????msg_receive??????????????????????????????????????????
     * ?????????msg_receive????????????
     *
     * @param params ??????????????????
     * @return ????????????
     */
    @ApiOperation(value = "????????????????????????", notes = "????????????????????????")
    @PostMapping("/page")
    @SysLog(value = "'??????????????????:???' + #params?.current + '???, ??????' + #params?.size + '???'", response = false)
    public R<IPage<Msg>> page(@RequestBody @Validated PageParams<MsgQuery> params) {
        IPage<Msg> page = params.buildPage();
        Msg model = BeanUtil.toBean(params.getModel(), Msg.class);
        LbqWrapper<Msg> wraps = Wraps.lbq(model, params.getExtra(), Msg.class);
        msgService.page(page, wraps);
        return R.success(page);
    }

    private IPage<MsgPageResult> query(PageParams<MsgQuery> param, IPage<MsgPageResult> page) {

        MsgQuery model = param.getModel();
        PageUtil.timeRange(param);

        model.setUserId(ContextUtil.getUserId());
        msgService.page(page, param);
        return page;
    }


    /**
     * ??????????????????
     *
     * @param params ????????????
     * @param page   ????????????
     * @return ????????????
     */
    private ExportParams getExportParams(PageParams<MsgQuery> params, IPage<MsgPageResult> page) {
        query(params, page);

        Object title = params.getExtra().get("title");
        Object type = params.getExtra().getOrDefault("type", ExcelType.XSSF.name());
        Object sheetName = params.getExtra().getOrDefault("sheetName", "SheetName");

        ExcelType excelType = ExcelType.XSSF.name().equals(type) ? ExcelType.XSSF : ExcelType.HSSF;
        return new ExportParams(String.valueOf(title), sheetName.toString(), excelType);
    }

    /**
     * ??????Excel
     *
     * @param params   ??????
     * @param request  ??????
     * @param response ??????
     */
    @ApiOperation(value = "??????Excel")
    @RequestMapping(value = "/export", method = RequestMethod.POST, produces = "application/octet-stream")
    @SysLog("'??????Excel:'.concat(#params.extra[" + NormalExcelConstants.FILE_NAME + "]?:'')")
    public void exportExcel(@RequestBody @Validated PageParams<MsgQuery> params, HttpServletRequest request, HttpServletResponse response) {
        IPage<MsgPageResult> page = params.buildPage();
        ExportParams exportParams = getExportParams(params, page);

        Map<String, Object> map = new HashMap<>(7);
        map.put(NormalExcelConstants.DATA_LIST, page.getRecords());
        map.put(NormalExcelConstants.CLASS, MsgPageResult.class);
        map.put(NormalExcelConstants.PARAMS, exportParams);
        Object fileName = params.getExtra().getOrDefault(NormalExcelConstants.FILE_NAME, "????????????");
        map.put(NormalExcelConstants.FILE_NAME, fileName);
        PoiBaseView.render(map, request, response, NormalExcelConstants.EASYPOI_EXCEL_VIEW);
    }

    /**
     * ??????Excel
     *
     * @param params ????????????
     * @return ????????????
     */
    @ApiOperation(value = "??????Excel")
    @SysLog("'??????Excel:' + (#params.extra[" + NormalExcelConstants.FILE_NAME + "]?:'')")
    @RequestMapping(value = "/preview", method = RequestMethod.POST)
    public R<String> preview(@RequestBody @Validated PageParams<MsgQuery> params) {
        IPage<MsgPageResult> page = params.buildPage();
        ExportParams exportParams = getExportParams(params, page);

        Workbook workbook = ExcelExportUtil.exportExcel(exportParams, MsgPageResult.class, page.getRecords());
        return R.success(ExcelXorHtmlUtil.excelToHtml(new ExcelToHtmlParams(workbook)));
    }

    /**
     * ?????????????????????
     *
     * @param msgCenterIds ??????id
     * @return ????????????
     */
    @ApiOperation(value = "?????????????????????", notes = "?????????????????????")
    @PostMapping(value = "/mark")
    public R<Boolean> mark(@RequestBody List<Long> msgCenterIds) {
        return R.success(msgService.mark(msgCenterIds, ContextUtil.getUserId()));
    }

    /**
     * ??????????????????
     *
     * @param id ??????id
     * @return ????????????
     */
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @GetMapping("/{id}")
    @SysLog("??????????????????")
    public R<Msg> get(@PathVariable Long id) {
        return R.success(msgService.getById(id));
    }

    /**
     * ??????????????????
     *
     * @param data ????????????
     * @return ????????????
     */
    @ApiOperation(value = "??????????????????", notes = "????????????????????????????????????")
    @PostMapping
    @SysLog("??????????????????")
    @PreAuth("hasAnyPermission('{}add')")
    public R<Msg> save(@RequestBody @Validated MsgSaveDTO data) {
        if (CollectionUtil.isEmpty(data.getUserIdList()) && CollectionUtil.isNotEmpty(data.getRoleCodeList())) {
            R<List<Long>> result = roleApi.findUserIdByCode(data.getRoleCodeList().toArray(new String[0]));
            if (result.getIsSuccess()) {
                if (result.getData().isEmpty()) {
                    return R.fail("???????????????????????????????????????");
                }
                data.setUserIdList(new HashSet<>(result.getData()));
            }
        }
        if (MsgType.PUBLICITY.eq(data.getMsgDTO().getMsgType())) {
            R<List<Long>> result = userBizApi.findAllUserId();
            if (result.getIsSuccess()) {
                data.setUserIdList(new HashSet<>(result.getData()));
            }
        }

        return R.success(msgService.saveMsg(data));
    }

    /**
     * ??????????????????
     *
     * @param ids ??????id
     * @return ????????????
     */
    @ApiOperation(value = "??????????????????", notes = "??????id????????????????????????")
    @DeleteMapping
    @SysLog("??????????????????")
    public R<Boolean> delete(@RequestParam(value = "ids[]") List<Long> ids) {
        return R.success(msgService.delete(ids, ContextUtil.getUserId()));
    }

}
