package controller.analysis

import controller.analysis.detection.CloneDetector
import controller.analysis.detection.CloneMetricsCalculator
import controller.analysis.parsing.Parser
import model.AnalysisRequest
import model.AnalysisResponse
import model.CloneMetrics
import model.Unit
import utility.Clone
import utility.toJson
import java.io.File


class AnalysisController {
    fun analyze(analysisRequest: AnalysisRequest): AnalysisResponse {
        val startTime: Long = System.currentTimeMillis()
        val units: List<Unit> = Parser(analysisRequest.basePath, analysisRequest.projectRoot, analysisRequest.cloneType).parse()

        val cloneDetector = CloneDetector(analysisRequest.basePath, units, analysisRequest.massThreshold, (100).toDouble(), analysisRequest.cloneType) // TODO: add similarity Threshold as a request parameter
        val (clones: List<Clone>, cloneClasses: List<Set<Unit>>) = cloneDetector.detectClones()

        val sequenceCloneClasses: List<Set<Unit>> = cloneDetector.findSequenceCloneClasses(clones).toList()
        val cloneClassesFiltered: List<Set<Unit>> = cloneDetector.filterOutClonesIncludedInSequenceClasses(cloneClasses, sequenceCloneClasses)

        val allClones: List<Set<Unit>> = cloneClassesFiltered + sequenceCloneClasses

        val metrics: CloneMetrics = CloneMetricsCalculator(allClones, units).calculateMetrics() // TODO: Calculate metrics using sequenceCloneClasses and cloneClassesFiltered
        return constructAnalysisResponse(allClones, metrics)
            .also { println("The analysis of project '${analysisRequest.basePath}' took ${(System.currentTimeMillis() - startTime) / 1000} seconds.") }
    }

    private fun constructAnalysisResponse(cloneClasses: List<Set<Unit>>, metrics: CloneMetrics): AnalysisResponse {
        return AnalysisResponse(cloneClasses = cloneClasses, metrics = metrics)
    }

    companion object {
        fun cloneRepository(gitUri: String): File {
            val projectDirectory = File("/tmp/${gitUri.substringAfterLast('/')}")
            val process: Process = ProcessBuilder("git", "clone", gitUri, projectDirectory.absolutePath).start()
            process.waitFor()
            return projectDirectory
        }

        fun normalizeBasePath(basePath: String): String {
            return if (basePath.startsWith('/')) {
                basePath
            } else {
                "/$basePath"
            }
        }

        fun writeResponseToFile(analysisRequest: AnalysisRequest, response: AnalysisResponse) {
            File("${analysisRequest.projectRoot.absolutePath}/report.json").also { it.delete() }.also { it.createNewFile() }.writeText(response.toJson())
        }
    }
}
