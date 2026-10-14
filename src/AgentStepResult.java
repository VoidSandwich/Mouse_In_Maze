/**
 * Результат одного действия мыши.
 */
public record AgentStepResult(
        Position position,
        Direction action,
        double reward,
        double score,
        int episodeStep,
        long completedEpisodes,
        boolean wallHit,
        boolean waterConsumed,
        boolean trapConsumed,
        boolean episodeFinished,
        boolean success,
        String finishReason,
        double epsilon,
        double bestScore) {
}
