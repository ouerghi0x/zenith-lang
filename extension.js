const vscode = require('vscode');

function activate(context) {
    let runCmd = vscode.commands.registerCommand('zenith.run', function () {
        const editor = vscode.window.activeTextEditor;
        const filePath = editor ? editor.document.fileName : 'schema.zen';
        let terminal = vscode.window.terminals.find(t => t.name === 'Zenith') || vscode.window.createTerminal('Zenith');
        terminal.show();
        terminal.sendText(`.\\bin\\zenith.bat build "${filePath}"`);
    });

    let studioCmd = vscode.commands.registerCommand('zenith.studio', function () {
        let terminal = vscode.window.terminals.find(t => t.name === 'Zenith Studio') || vscode.window.createTerminal('Zenith Studio');
        terminal.show();
        terminal.sendText('.\\bin\\zenith.bat studio');
    });

    context.subscriptions.push(runCmd, studioCmd);
}

function deactivate() {}

module.exports = { activate, deactivate };
