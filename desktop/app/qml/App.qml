// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQml
import app.nectarlink

QtObject {
    id: app

    readonly property Loader mainLoader: Loader {
        active: true
        source: "qrc:/qt/qml/app/nectarlink/qml/TransferOnlyWindow.qml"
    }
    property var islandWindow: null
    readonly property Component islandWindowComponent: Component {
        IslandWindow {}
    }

    Component.onCompleted: {
        AppController.activateRequested.connect(() => mainLoader.item.requestActivate())
        AppController.quitRequested.connect(() => Qt.quit())
        const w = islandWindowComponent.createObject(app)
        if (w)
            islandWindow = w
        else
            console.warn("Transfer progress popup could not be created:", islandWindowComponent.errorString())
    }
}
