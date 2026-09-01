tasks.register<Exec>("npmInstall") {
    workingDir(projectDir)
    commandLine("npm", "install")
}

tasks.register<Exec>("assembleDebug") {
    dependsOn("npmInstall")
    workingDir(projectDir)
    commandLine("npm", "run", "build")
}
