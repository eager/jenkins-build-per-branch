package com.entagen.jenkins

import java.util.regex.Pattern

class GitApi {
    String gitUrl
    Pattern branchNameFilter = null

    public void cloneRepo() {

        // TODO make sure we need to clone
        String[] command = ["git", "clone", gitUrl]

        try {
            eachResultLine(command) { String line ->

            }
        } catch (e) {
            // TODO remove catch when we check if we need to clone above
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

        cloneRepo()
        checkoutBranch(branch)
        mergeLatestFromOrigin(branch)

        String[] command = ["git", getGitDir(), "log", "--pretty=%h", "--since=${since}"]
        List<String> commits = []

        eachResultLine(command) { String line ->
            commits << line.trim()
        }

        return commits
    }

    public List<String> getBranchNamesMergedWithMaster() {
        String[] command = ["/bin/sh", "-c", "git ${getGitDir()} branch -r --merged "
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

    private String getGitDir() {
        return "--git-dir=${getRepoName()}/.git"
    }

    private void checkoutBranch(String branch) {
        String[] command = ["git", getGitDir(), "checkout", "-b", branch]

        try {
            eachResultLine(command) { String line ->
                // Nothing
            }
        } catch (e) {
            // TODO check if the branch creation is necessary
        }
    }

    private void mergeLatestFromOrigin(branch) {
        String[] command = ["git", getGitDir(), "merge", "--ff-only", "origin/${branch}"]

        try {
            eachResultLine(command) { String line ->
                // Nothing
            }
        } catch (e) {
            // Assume it failed because it’s already up-to-date
        }
    }
}
