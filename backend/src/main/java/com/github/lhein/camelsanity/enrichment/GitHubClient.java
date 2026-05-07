package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.config.AnalyzerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private static final Pattern REPO_PATTERN =
            Pattern.compile("github\\.com[/:]([^/]+)/([^/.]+)(?:\\.git)?");

    private final WebClient client;
    private final AnalyzerProperties props;

    public GitHubClient(WebClient client, AnalyzerProperties props) {
        this.client = client;
        this.props = props;
    }

    /**
     * Extracts owner/repo from any URL containing github.com/owner/repo.
     */
    public static Optional<OwnerRepo> parseRepo(String url) {
        if (url == null) return Optional.empty();
        Matcher m = REPO_PATTERN.matcher(url);
        if (m.find()) {
            String owner = m.group(1);
            String repo = m.group(2).replaceAll("\\.git$", "");
            return Optional.of(new OwnerRepo(owner, repo));
        }
        return Optional.empty();
    }

    @Cacheable(value = "github-repo", key = "#owner + '/' + #repo")
    public RepoInfo fetchRepo(String owner, String repo) {
        try {
            Map<String, Object> body = get("/repos/" + owner + "/" + repo, Map.class);
            if (body == null) return RepoInfo.empty();

            Instant pushedAt = parseInstant((String) body.get("pushed_at"));
            Integer stars = toInt(body.get("stargazers_count"));
            Integer openIssues = toInt(body.get("open_issues_count"));
            Boolean archived = (Boolean) body.get("archived");
            String homepage = (String) body.get("homepage");
            String htmlUrl = (String) body.get("html_url");

            // Latest release date
            Instant latestRelease = null;
            try {
                Map<String, Object> rel = get("/repos/" + owner + "/" + repo + "/releases/latest", Map.class);
                if (rel != null) {
                    latestRelease = parseInstant((String) rel.get("published_at"));
                }
            } catch (Exception ignored) {
                // 404 if no releases
            }

            // Contributors count: HEAD request with per_page=1, parse Link header for last page
            Integer contributors = fetchContributorsCount(owner, repo);

            return new RepoInfo(htmlUrl, homepage, pushedAt, stars, openIssues,
                    archived, latestRelease, contributors);
        } catch (Exception e) {
            log.debug("GitHub fetch failed for {}/{}: {}", owner, repo, e.getMessage());
            return RepoInfo.empty();
        }
    }

    private Integer fetchContributorsCount(String owner, String repo) {
        try {
            // anon=true counts anonymous contributors too
            String path = "/repos/" + owner + "/" + repo + "/contributors?per_page=1&anon=true";
            var response = client.get()
                    .uri("https://api.github.com" + path)
                    .headers(this::applyHeaders)
                    .retrieve()
                    .toEntity(List.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (response == null) return null;
            String link = response.getHeaders().getFirst("Link");
            if (link != null) {
                Matcher m = Pattern.compile("page=(\\d+)>; rel=\"last\"").matcher(link);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            // No Link header → at most 1 page → at most 1 contributor (since per_page=1)
            List<?> firstPage = response.getBody();
            return firstPage == null ? 0 : firstPage.size();
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T get(String path, Class<T> type) {
        return client.get()
                .uri("https://api.github.com" + path)
                .headers(this::applyHeaders)
                .retrieve()
                .bodyToMono(type)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    private void applyHeaders(HttpHeaders headers) {
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        if (props.github().hasToken()) {
            headers.set("Authorization", "Bearer " + props.github().token());
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return null;
    }

    public record OwnerRepo(String owner, String repo) {
        public String slug() {
            return owner + "/" + repo;
        }
    }

    public record RepoInfo(
            String htmlUrl,
            String homepage,
            Instant lastPush,
            Integer stars,
            Integer openIssues,
            Boolean archived,
            Instant latestRelease,
            Integer contributors) {
        public static RepoInfo empty() {
            return new RepoInfo(null, null, null, null, null, null, null, null);
        }
    }
}
