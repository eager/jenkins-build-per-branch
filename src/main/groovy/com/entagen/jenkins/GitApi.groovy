package com.entagen.jenkins

import java.util.regex.Pattern

class GitApi {
    String gitUrl
    Pattern branchNameFilter = null

    public void cloneOrFetchRepo() {

        if (repoExists()) {

            String[] command = getGitCommand(["fetch", "origin"])
            eachResultLine(command) { String line -> }

        } else {

            String[] command = ["git", "clone", gitUrl]
            eachResultLine(command) { String line -> }
        }
    }

    public List<String> getBranchNames() {
        String[] command = ["git", "ls-remote", "--heads", gitUrl]
        List<String> branchNames = []

        eachResultLine(command) { String line ->
            println "\t$line"
            // lines are in the format of: <SHA>\trefs/heads/BRANCH_NAME
            // ex: b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
            String branchNameRegex = "^.*\trefs/heads/(.*)\$"
            String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
            if (passesFilter(branchName)) branchNames << branchName
        }

        return branchNames
    }

    public List<String> getCommitsSince(String branch, String since) {

        if (!repoExists()) {
            cloneOrFetchRepo()
        }

        String[] command = ["git", getGitDir(), "log", "--pretty=%h", "--since=${since}", "origin/${branch}"]
        List<String> commits = []

        eachResultLine(command) { String line ->
            if (line.trim()) {
                commits << line.trim()
            }
        }

        return commits
    }

    public List<String> getBranchNamesMergedWithMaster() {

        if (!repoExists()) {
            cloneOrFetchRepo()
        }

        String[] command = ["/bin/sh", "-c", "git ${getGitDir()} branch -r --merged origin/master "
                + " | perl -pe s/^\\s+//"
                + " |  grep -v master "
                + " | grep -v HEAD"]
        List<String> branchNames = []

        eachResultLine(command) { String line ->
            branchNames << line.split("origin/").last().trim()
        }

        return branchNames
    }

    public Boolean passesFilter(String branchName) {
        if (!branchName) return false
        if (!branchNameFilter) return true
        return branchName ==~ branchNameFilter
    }

    // assumes all commands are "safe", if we implement any destructive git commands, we'd want to separate those out for a dry-run
    public void eachResultLine(String[] command, Closure closure) {
        println "executing command: $command"

        def process = new ProcessBuilder(command).start()
        def inputStream = process.getInputStream()
        def gitOutput = ""

        while (true) {
            int readByte = inputStream.read()
            if (readByte == -1) break // EOF
            byte[] bytes = new byte[1]
            bytes[0] = readByte
            gitOutput = gitOutput.concat(new String(bytes))
        }
        process.waitFor()

        if (process.exitValue() == 0) {
            gitOutput.eachLine { String line ->
                closure(line)
            }
        } else {
            String errorText = process.errorStream.text?.trim()
            println "error executing command: $command"
            println errorText
            throw new Exception("Error executing command: $command -> $errorText")
        }
    }

    private String getRepoName() {
        return gitUrl.split("/").last().split(".git").first()
    }

    private void createTrackingBranch(String branch) {

        String[] command = getGitCommand(["branch", "--track", branch, "origin/$branch"])
        eachResultLine(command) { String line -> }
    }

    private List<String> getGitCommand(List<String> subCommands) {
        List<String> command = ["git", getGitDir(), getWorkTree()]
        command.addAll(subCommands)
        return command
    }


    private String getGitDir() {
        return "--git-dir=${getRepoName()}/.git"
    }

    private String getWorkTree() {
        return "--work-tree=${getRepoName()}"
    }

    private boolean repoExists() {
        return new File(getRepoName()).exists()
    }
}
