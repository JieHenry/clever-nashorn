// 初始化配置
require.config({
    paths: {'vs': '../monaco-editor/min/vs'},
    'vs/nls': {
        availableLanguages: {
            '*': 'zh-cn'
        }
    }
});

// // 全屏/取消全屏
// function fullscreen(full) {
//     var node = $(".fullscreen");
//     if (full === true) {
//         node.addClass("active");
//     } else if (full === false) {
//         node.removeClass("active");
//     } else {
//         node.toggleClass("active");
//     }
// }

// // 全屏事件
// $(".fullscreen").on("click", function () {
//     if (fullscreenFunc) {
//         fullscreen();
//         fullscreenFunc();
//     }
// });

var editor;
var fullscreenFunc;

// 初始化编辑器
function initEditor(config, setEditorFunc) {
    if (config) {
        fullscreenFunc = config.fullscreen;
    }
    require(['vs/editor/editor.main'], function () {
        // console.log(monaco.editor);
        // 自定义编辑器 https://github.com/microsoft/monaco-editor-samples/blob/master/browser-amd-monarch/index.html
        // monaco.languages.register({id: 'nashorn-js'});
        // monaco.languages.setMonarchTokensProvider('nashorn-js', nashornJS);
        // 属性配置 https://microsoft.github.io/monaco-editor/api/interfaces/monaco.editor.ieditorconstructionoptions.html
        var options = {
            // 主题，三款：vs、vs-dark、hc-black
            theme: 'vs',
            // 内容
            value: '',
            // 语言
            language: 'javascript',
            // 显示行号
            lineNumbers: true,
            // 只读属性
            readOnly: false,
            // 光标样式 'block' or 'line'
            cursorStyle: 'line',
            // 字体大小
            fontSize: 14,
            // 自适应调整
            automaticLayout: false,
            // 右键菜单
            contextmenu: true,
            //代码略缩图
            minimap: {
                enabled: false
            }
        };
        options = $.extend(true, options, config);
        if (options.theme === "vs") {
            $(".fullscreen").attr("data-theme", "vs");
        } else {
            $(".fullscreen").attr("data-theme", "vs-dark");
        }
        // 创建编辑器
        editor = monaco.editor.create(document.getElementById('container'), options);
        // 增加快捷键(可覆盖默认快捷键)
        editor.addCommand(
            monaco.KeyMod.Alt | monaco.KeyCode.US_SLASH,
            function () {
                editor.trigger(null, 'editor.action.triggerSuggest', {});
            },
            '!findWidgetVisible && !inReferenceSearchEditor && !editorHasSelection'
        );
        editor.addCommand(
            monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KEY_U,
            function () {
                editor.trigger(null, 'editor.action.transformToUppercase', {});
            }
        );
        editor.addCommand(
            monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KEY_I,
            function () {
                editor.trigger(null, 'editor.action.transformToLowercase', {});
            }
        );
        if (setEditorFunc) setEditorFunc(editor);
    });
}

// 设置值
function setValue(value) {
    editor.setValue(value);
}

// 获取值
function getValue() {
    return editor.getValue();
}

// 内容改变事件
// editor.onDidChangeModelContent(function (e) {
//   console.log(e);
// });

// 设置主题 vs、vs-dark、hc-black
function setTheme(theme) {
    monaco.editor.setTheme(theme);
}

// 改变属性
function updateOptions(config) {
    editor.updateOptions(config || {});
}

// 大小发生变化
window.addEventListener(
    'resize',
    _.debounce(
        function () {
            if (!editor) return;
            editor.layout();
        },
        100,
        {maxWait: 350}
    )
);