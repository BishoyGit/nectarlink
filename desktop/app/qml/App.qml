// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQml
import app.nectarlink

TransferOnlyWindow {
    id: app

    readonly property bool startMinimized: Qt.application.arguments.indexOf("--minimized") >= 0
        || Qt.application.arguments.indexOf("--send-to") >= 0
    visible: !startMinimized

    property var islandWindow: null
    readonly property Component islandWindowComponent: Component {
        IslandWindow {}
    }

    Component.onCompleted: {
        AppController.activateRequested.connect(() => {
            app.visible = true
            app.requestActivate()
        })
        AppController.quitRequested.connect(() => Qt.quit())
        const w = islandWindowComponent.createObject(app)
        if (w)
            islandWindow = w
        else
            console.warn("Transfer progress popup could not be created:", islandWindowComponent.errorString())
        if (!startMinimized) {
            app.visible = true
            app.requestActivate()
        }
    }
}
