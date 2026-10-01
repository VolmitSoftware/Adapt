#if defined(__aarch64__)
#include <arm_acle.h>
#endif
#include <QtCore/QCoreApplication>
#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QProcess>
#include <QtCore/QSettings>
#include <cstdio>

int main(int argc, char **argv) {
    QCoreApplication application(argc, argv);
    if (argc != 2) {
        return 2;
    }
    QSettings settings(QString::fromLocal8Bit(argv[1]), QSettings::IniFormat);
    QJsonArray arguments;
    for (const QString &argument : QProcess::splitCommand(settings.value("JvmArgs").toString())) {
        arguments.append(argument);
    }
    QByteArray json = QJsonDocument(arguments).toJson(QJsonDocument::Compact);
    std::fwrite(json.constData(), 1, json.size(), stdout);
}
