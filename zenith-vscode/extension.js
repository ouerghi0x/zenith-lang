const vscode = require('vscode');

function activate(context) {
    // 1. Command: Build backend
    let runCmd = vscode.commands.registerCommand('zenith.run', function (uri) {
        const file = uri ? uri.fsPath : (vscode.window.activeTextEditor ? vscode.window.activeTextEditor.document.fileName : 'schema.zen');
        let terminal = vscode.window.terminals.find(t => t.name === 'Zenith');
        if (!terminal) {
            terminal = vscode.window.createTerminal('Zenith');
        }
        terminal.show();
        terminal.sendText(`.\\bin\\zenith.bat build "${file}"`);
    });

    // 2. Command: Open Zenith Studio
    let studioCmd = vscode.commands.registerCommand('zenith.studio', function () {
        let terminal = vscode.window.terminals.find(t => t.name === 'Zenith Studio');
        if (!terminal) {
            terminal = vscode.window.createTerminal('Zenith Studio');
        }
        terminal.show();
        terminal.sendText('.\\bin\\zenith.bat studio');
    });

    context.subscriptions.push(runCmd, studioCmd);
}

function deactivate() {}

module.exports = {
    activate,
    deactivate
};
