package com.tale.controller.admin;

import com.blade.ioc.annotation.Inject;
import com.blade.kit.StringKit;
import com.blade.kit.Tools;
import com.blade.kit.base.Config;
import com.blade.kit.json.JSONKit;
import com.blade.mvc.annotation.Controller;
import com.blade.mvc.annotation.JSON;
import com.blade.mvc.annotation.QueryParam;
import com.blade.mvc.annotation.Route;
import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.http.Request;
import com.blade.mvc.view.RestResponse;
import com.tale.controller.BaseController;
import com.tale.dto.BackResponse;
import com.tale.dto.LogActions;
import com.tale.dto.Statistics;
import com.tale.dto.Types;
import com.tale.exception.TipException;
import com.tale.init.TaleConst;
import com.tale.model.Comments;
import com.tale.model.Contents;
import com.tale.model.Logs;
import com.tale.model.Users;
import com.tale.service.LogService;
import com.tale.service.OptionsService;
import com.tale.service.SiteService;
import com.tale.service.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 后台控制器
 * Created by biezhi on 2017/2/21.
 */
@Controller("admin")
public class IndexController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexController.class);

    @Inject
    private OptionsService optionsService;

    @Inject
    private SiteService siteService;

    @Inject
    private UsersService usersService;

    @Inject
    private LogService logService;

    /**
     * 仪表盘
     */
    @Route(value = {"/", "index"}, method = HttpMethod.GET)
    public String index(Request request) {
        List<Comments> comments = siteService.recentComments(5);
        List<Contents> contents = siteService.getContens(Types.RECENT_ARTICLE, 5);
        Statistics statistics = siteService.getStatistics();
        // 取最新的20条日志
        List<Logs> logs = logService.getLogs(1, 20);

        request.attribute("comments", comments);
        request.attribute("articles", contents);
        request.attribute("statistics", statistics);
        request.attribute("logs", logs);
        return "admin/index";
    }

    /**
     * 系统设置
     */
    @Route(value = "setting", method = HttpMethod.GET)
    public String setting(Request request) {
        Map<String, String> options = optionsService.getOptions();
        request.attribute("options", options);
        // 读取主题
        String themesDir = AttachController.CLASSPATH + "templates/themes";
        File[] themesFile = new File(themesDir).listFiles();
        List<String> themems = new ArrayList<>(themesFile.length);
        for(File f : themesFile){
            if(f.isDirectory()){
                themems.add(f.getName());
            }
        }
        request.attribute("themes", themems);
        return "admin/setting";
    }

    /**
     * 保存系统设置
     */
    @Route(value = "setting", method = HttpMethod.POST)
    @JSON
    public RestResponse saveSetting(@QueryParam String site_theme, Request request) {
        try {
            Map<String, String> querys = request.querys();
            optionsService.saveOptions(querys);

            Config config = new Config();
            config.addAll(optionsService.getOptions());
            TaleConst.OPTIONS = config;

            if (StringKit.isNotBlank(site_theme)) {
                BaseController.THEME = "themes/" + site_theme;
            }
            logService.save(LogActions.SYS_SETTING, JSONKit.toJSONString(querys), request.address(), this.getUid());
            return RestResponse.ok();
        } catch (Exception e) {
            String msg = "保存设置失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 个人设置页面
     */
    @Route(value = "profile", method = HttpMethod.GET)
    public String profile() {
        return "admin/profile";
    }

    /**
     * 保存个人信息
     */
    @Route(value = "profile", method = HttpMethod.POST)
    @JSON
    public RestResponse saveProfile(@QueryParam String screen_name, @QueryParam String email, Request request) {
        Users users = this.user();
        if (StringKit.isNotBlank(screen_name) && StringKit.isNotBlank(email)) {
            Users temp = new Users();
            temp.setUid(users.getUid());
            temp.setScreen_name(screen_name);
            temp.setEmail(email);
            usersService.update(temp);
            logService.save(LogActions.UP_INFO, JSONKit.toJSONString(temp), request.address(), this.getUid());
        }
        return RestResponse.ok();
    }

    /**
     * 修改密码
     */
    @Route(value = "password", method = HttpMethod.POST)
    @JSON
    public RestResponse upPwd(@QueryParam String old_password, @QueryParam String password, Request request) {
        Users users = this.user();
        if (StringKit.isBlank(old_password) || StringKit.isBlank(password)) {
            return RestResponse.fail("请确认信息输入完整");
        }

        if (!users.getPassword().equals(Tools.md5(users.getUsername() + old_password))) {
            return RestResponse.fail("旧密码错误");
        }
        if (password.length() < 6 || password.length() > 14) {
            return RestResponse.fail("请输入6-14位密码");
        }

        try {
            Users temp = new Users();
            temp.setUid(users.getUid());
            String pwd = Tools.md5(users.getUsername() + password);
            temp.setPassword(pwd);
            usersService.update(temp);
            logService.save(LogActions.UP_PWD, null, request.address(), this.getUid());
            return RestResponse.ok();
        } catch (Exception e){
            String msg = "密码修改失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 系统备份
     * @return
     */
    @Route(value = "backup", method = HttpMethod.POST)
    @JSON
    public RestResponse backup(@QueryParam String bk_type, @QueryParam String bk_path,
                               Request request) {
        if (StringKit.isBlank(bk_type)) {
            return RestResponse.fail("请确认信息输入完整");
        }
        try {
            BackResponse backResponse = siteService.backup(bk_type, bk_path, "yyyyMMddHHmm");
            logService.save(LogActions.SYS_BACKUP, null, request.address(), this.getUid());
            return RestResponse.ok(backResponse);
        } catch (Exception e){
            String msg = "备份失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 后台高级选项页面
     *
     * @return
     */
    @Route(value = "advanced", method = HttpMethod.GET)
    public String advanced(Request request){
        Map<String, String> options = optionsService.getOptions();
        request.attribute("options", options);
        return "admin/advanced";
    }

    /**
     * 保存高级选项设置
     * @return
     */
    @Route(value = "advanced", method = HttpMethod.POST)
    public String doAdvanced(@QueryParam String cache_key, @QueryParam String block_ips){
        // 清除缓存
        if(StringKit.isNotBlank(cache_key)){
            if(cache_key.equals("*")){
                cache.clean();
            } else {
                cache.del(cache_key);
            }
        }
        // 要过过滤的黑名单列表
        if(StringKit.isNotBlank(block_ips)){
            optionsService.saveOption(Types.BLOCK_IPS, block_ips);
            TaleConst.BLOCK_IPS.addAll(Arrays.asList(StringKit.split(block_ips, ",")));
        } else {
            optionsService.saveOption(Types.BLOCK_IPS, "");
            TaleConst.BLOCK_IPS.clear();
        }
        return "admin/advanced";
    }

}
