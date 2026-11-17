// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import QtQuick.Window
import app.nectarlink

Window {
    id: window

    width: 760
    height: 620
    minimumWidth: 560
    minimumHeight: 420
    visible: true
    title: "NectarDrop"
    color: Theme.surface

    readonly property int selectedDeviceIndex: DeviceList.count > 0
        ? Math.min(Math.max(0, AppController.currentDevice), DeviceList.count - 1) : -1
    readonly property string selectedDeviceId: selectedDeviceIndex >= 0
        ? DeviceList.deviceIdAt(selectedDeviceIndex) : ""
    readonly property string selectedDeviceName: selectedDeviceIndex >= 0
        ? DeviceList.deviceNameAt(selectedDeviceIndex) : ""

    FileDialog {
        id: filesDialog
        title: qsTr("Choose files to send")
        fileMode: FileDialog.OpenFiles
        onAccepted: TransferList.send(window.selectedDeviceId, selectedFiles.map(url => url.toString()))
    }

    FolderDialog {
        id: folderDialog
        title: qsTr("Choose a folder to send")
        onAccepted: TransferList.send(window.selectedDeviceId, [selectedFolder.toString()])
    }

    Rectangle {
        anchors.fill: parent
        color: Theme.surface

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 28
            spacing: 18

            RowLayout {
                Layout.fillWidth: true
                spacing: 12

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 3
                    Text {
                        text: "NectarDrop"
                        color: Theme.surfaceContent
                        font.pixelSize: 26
                        font.weight: Font.DemiBold
                    }
                    Text {
                        text: qsTr("Send and receive files between your devices")
                        color: Theme.surfaceContentVariant
                        font.pixelSize: 13
                    }
                }

                Button {
                    text: qsTr("Pair device")
                    onClicked: pairingOverlay.visible = true
                }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 12
                Text {
                    text: qsTr("Send to")
                    color: Theme.surfaceContent
                    font.pixelSize: 14
                }
                ComboBox {
                    id: devicePicker
                    Layout.fillWidth: true
                    model: DeviceList
                    textRole: "name"
                    currentIndex: window.selectedDeviceIndex
                    onActivated: AppController.currentDevice = currentIndex
                    enabled: DeviceList.count > 0
                }
                Text {
                    text: window.selectedDeviceIndex < 0
                        ? qsTr("Pair a device to start")
                        : (DeviceList.data(DeviceList.index(window.selectedDeviceIndex, 0), Qt.UserRole + 8)
                           ? qsTr("Connected") : qsTr("Offline — files will queue"))
                    color: window.selectedDeviceIndex < 0 ? Theme.surfaceContentVariant : Theme.primary
                    font.pixelSize: 13
                }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 10
                Button {
                    text: qsTr("Send files…")
                    enabled: window.selectedDeviceId.length > 0
                    onClicked: filesDialog.open()
                }
                Button {
                    text: qsTr("Send folder…")
                    enabled: window.selectedDeviceId.length > 0
                    onClicked: folderDialog.open()
                }
                Item { Layout.fillWidth: true }
                Button {
                    text: qsTr("Clear finished")
                    visible: TransferList.count > TransferList.active
                    onClicked: TransferList.clearFinished()
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                radius: 16
                color: Theme.surfaceContainer
                border.color: Theme.outlineVariant
                border.width: 1

                DropArea {
                    anchors.fill: parent
                    onDropped: (drop) => {
                        if (window.selectedDeviceId.length > 0 && drop.hasUrls)
                            TransferList.send(window.selectedDeviceId, drop.urls.map(url => url.toString()))
                    }

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: 12

                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Transfers")
                            color: Theme.surfaceContent
                            font.pixelSize: 18
                            font.weight: Font.DemiBold
                        }

                        Text {
                            Layout.fillWidth: true
                            visible: TransferList.count === 0
                            text: qsTr("Choose files or folders above, or drop them here.")
                            color: Theme.surfaceContentVariant
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                            Layout.fillHeight: true
                        }

                        ListView {
                            id: transfers
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            visible: TransferList.count > 0
                            model: TransferList
                            spacing: 10
                            clip: true

                            delegate: Rectangle {
                                required property string transferId
                                required property string deviceName
                                required property bool incoming
                                required property string title
                                required property real total
                                required property real done
                                required property real progress
                                required property string status
                                required property string reason

                                width: transfers.width
                                height: row.implicitHeight + 20
                                radius: 12
                                color: Theme.surface
                                border.color: Theme.outlineVariant

                                RowLayout {
                                    id: row
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 12

                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 6
                                        Text {
                                            Layout.fillWidth: true
                                            text: title.length > 0 ? title : qsTr("File transfer")
                                            color: Theme.surfaceContent
                                            elide: Text.ElideMiddle
                                        }
                                        Text {
                                            Layout.fillWidth: true
                                            text: (incoming ? qsTr("From") : qsTr("To")) + " " + deviceName
                                                + " · " + statusText(status, reason)
                                            color: Theme.surfaceContentVariant
                                            font.pixelSize: 12
                                        }
                                        ProgressBar {
                                            Layout.fillWidth: true
                                            visible: status === "running" || status === "waiting" || status === "paused"
                                            value: progress
                                        }
                                        Text {
                                            visible: status === "running" || status === "waiting" || status === "paused"
                                            text: Math.round(progress * 100) + "% · " + sizeText(done) + " / " + sizeText(total)
                                            color: Theme.surfaceContentVariant
                                            font.pixelSize: 11
                                        }
                                    }

                                    ColumnLayout {
                                        spacing: 4
                                        Button {
                                            visible: incoming && status === "requested"
                                            text: qsTr("Accept")
                                            onClicked: TransferList.accept(transferId)
                                        }
                                        Button {
                                            visible: status === "running"
                                            text: qsTr("Pause")
                                            onClicked: TransferList.pause(transferId)
                                        }
                                        Button {
                                            visible: status === "paused"
                                            text: qsTr("Resume")
                                            onClicked: TransferList.resume(transferId)
                                        }
                                        Button {
                                            visible: status === "requested" || status === "waiting"
                                                || status === "running" || status === "paused"
                                            text: qsTr("Cancel")
                                            onClicked: TransferList.cancel(transferId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text {
                Layout.fillWidth: true
                text: qsTr("Drop files anywhere in this window to send them to the selected device.")
                color: Theme.surfaceContentVariant
                font.pixelSize: 12
            }
        }
    }

    Rectangle {
        id: pairingOverlay
        anchors.fill: parent
        visible: false
        color: "#99000000"
        z: 2

        Rectangle {
            anchors.centerIn: parent
            width: Math.min(500, parent.width - 32)
            height: Math.min(610, parent.height - 32)
            radius: 18
            color: Theme.surface

            PairingPanel {
                id: pairingPanel
                anchors.fill: parent
                anchors.margins: 22
                running: pairingOverlay.visible
                onFinished: pairingOverlay.visible = false
                onCancelled: pairingOverlay.visible = false
            }
        }

        MouseArea {
            anchors.fill: parent
            z: -1
            onClicked: pairingOverlay.visible = false
        }
    }

    function sizeText(bytes) {
        if (bytes < 1024) return Math.round(bytes) + " B"
        const units = ["KB", "MB", "GB", "TB"]
        let value = bytes / 1024
        let unit = 0
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++ }
        return Qt.locale().toString(value, "f", 1) + " " + units[unit]
    }

    function statusText(status, reason) {
        if (status === "requested") return qsTr("Approval needed")
        if (status === "waiting") return qsTr("Waiting")
        if (status === "running") return qsTr("In progress")
        if (status === "paused") return qsTr("Paused")
        if (status === "done") return qsTr("Complete")
        if (status === "cancelled") return qsTr("Cancelled")
        if (status === "failed") return reason === "noSpace" ? qsTr("Not enough space") : qsTr("Failed")
        return status
    }
}
