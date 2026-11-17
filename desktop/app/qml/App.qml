// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQml
import app.nectarlink

QtObject {
    id: app

    readonly property bool startMinimized: Qt.application.arguments.indexOf("--minimized") >= 0
        || Qt.application.arguments.indexOf("--send-to") >= 0
    readonly property Loader mainLoader: Loader {
        active: false
        source: "qrc:/qt/qml/app/nectarlink/qml/TransferOnlyWindow.qml"
        onLoaded: {
            item.visible = true
            item.requestActivate()
        }
    }
    property var islandWindow: null
    readonly property Component islandWindowComponent: Component {
        IslandWindow {}
    }

    Component.onCompleted: {
        AppController.activateRequested.connect(() => app.showMain())
        AppController.quitRequested.connect(() => Qt.quit())
        const w = islandWindowComponent.createObject(app)
        if (w)
            islandWindow = w
        else
            console.warn("Transfer progress popup could not be created:", islandWindowComponent.errorString())
        if (!startMinimized)
            showMain()
    }

    function showMain() {
        if (!mainLoader.active || !mainLoader.item) {
            mainLoader.active = false
            mainLoader.active = true
            return
        }
        mainLoader.item.visible = true
        mainLoader.item.requestActivate()
    }
}
