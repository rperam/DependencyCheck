/*
 * This file is part of dependency-check-core.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2014 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.analyzer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.data.central.CentralSearch;
import org.owasp.dependencycheck.data.nexus.MavenArtifact;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Evidence;
import org.owasp.dependencycheck.jaxb.pom.PomUtils;
import org.owasp.dependencycheck.jaxb.pom.generated.Model;
import org.owasp.dependencycheck.jaxb.pom.generated.Organization;
import org.owasp.dependencycheck.utils.DownloadFailedException;
import org.owasp.dependencycheck.utils.Downloader;
import org.owasp.dependencycheck.utils.InvalidSettingException;
import org.owasp.dependencycheck.utils.Settings;

/**
 * Analyzer which will attempt to locate a dependency, and the GAV information, by querying Central for the dependency's SHA-1
 * digest.
 *
 * @author colezlaw
 */
public class CentralAnalyzer extends AbstractFileTypeAnalyzer {

    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CentralAnalyzer.class.getName());

    /**
     * The name of the analyzer.
     */
    private static final String ANALYZER_NAME = "Central Analyzer";

    /**
     * The phase in which this analyzer runs.
     */
    private static final AnalysisPhase ANALYSIS_PHASE = AnalysisPhase.INFORMATION_COLLECTION;

    /**
     * The types of files on which this will work.
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = newHashSet("jar");

    /**
     * The analyzer should be disabled if there are errors, so this is a flag to determine if such an error has occurred.
     */
    private boolean errorFlag = false;

    /**
     * The searcher itself.
     */
    private CentralSearch searcher;
    /**
     * Utility to read POM files.
     */
    private PomUtils pomUtil = new PomUtils();
    /**
     * Field indicating if the analyzer is enabled.
     */
    private final boolean enabled = checkEnabled();

    /**
     * Determine whether to enable this analyzer or not.
     *
     * @return whether the analyzer should be enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Determines if this analyzer is enabled.
     *
     * @return <code>true</code> if the analyzer is enabled; otherwise <code>false</code>
     */
    private boolean checkEnabled() {
        boolean retval = false;

        try {
            if (Settings.getBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED)) {
                if (!Settings.getBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED)
                        || NexusAnalyzer.DEFAULT_URL.equals(Settings.getString(Settings.KEYS.ANALYZER_NEXUS_URL))) {
                    LOGGER.fine("Enabling the Central analyzer");
                    retval = true;
                } else {
                    LOGGER.info("Nexus analyzer is enabled, disabling the Central Analyzer");
                }
            } else {
                LOGGER.info("Central analyzer disabled");
            }
        } catch (InvalidSettingException ise) {
            LOGGER.warning("Invalid setting. Disabling the Central analyzer");
        }
        return retval;
    }

    /**
     * Initializes the analyzer once before any analysis is performed.
     *
     * @throws Exception if there's an error during initialization
     */
    @Override
    public void initializeFileTypeAnalyzer() throws Exception {
        LOGGER.fine("Initializing Central analyzer");
        LOGGER.fine(String.format("Central analyzer enabled: %s", isEnabled()));
        if (isEnabled()) {
            final String searchUrl = Settings.getString(Settings.KEYS.ANALYZER_CENTRAL_URL);
            LOGGER.fine(String.format("Central Analyzer URL: %s", searchUrl));
            searcher = new CentralSearch(new URL(searchUrl));
        }
    }

    /**
     * Returns the analyzer's name.
     *
     * @return the name of the analyzer
     */
    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    /**
     * Returns the key used in the properties file to to reference the analyzer's enabled property.
     *
     * @return the analyzer's enabled property setting key.
     */
    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return Settings.KEYS.ANALYZER_CENTRAL_ENABLED;
    }

    /**
     * Returns the analysis phase under which the analyzer runs.
     *
     * @return the phase under which the analyzer runs
     */
    @Override
    public AnalysisPhase getAnalysisPhase() {
        return ANALYSIS_PHASE;
    }

    /**
     * Returns the extensions for which this Analyzer runs.
     *
     * @return the extensions for which this Analyzer runs
     */
    @Override
    public Set<String> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * Performs the analysis.
     *
     * @param dependency the dependency to analyze
     * @param engine the engine
     * @throws AnalysisException when there's an exception during analysis
     */
    @Override
    public void analyzeFileType(Dependency dependency, Engine engine) throws AnalysisException {
        if (errorFlag || !isEnabled()) {
            return;
        }

        try {
            final List<MavenArtifact> mas = searcher.searchSha1(dependency.getSha1sum());
            final Confidence confidence = mas.size() > 1 ? Confidence.HIGH : Confidence.HIGHEST;
            for (MavenArtifact ma : mas) {
                LOGGER.fine(String.format("Central analyzer found artifact (%s) for dependency (%s)", ma.toString(), dependency.getFileName()));
                dependency.addAsEvidence("central", ma, confidence);
                boolean pomAnalyzed = false;
                for (Evidence e : dependency.getVendorEvidence()) {
                    if ("pom".equals(e.getSource())) {
                        pomAnalyzed = true;
                        break;
                    }
                }
                if (!pomAnalyzed && ma.getPomUrl() != null) {
                    File pomFile = null;
                    try {
                        final File baseDir = Settings.getTempDirectory();
                        pomFile = File.createTempFile("pom", "xml", baseDir);
                        if (!pomFile.delete()) {
                            final String msg = String.format("Unable to fetch pom.xml for %s from Central; this could result in undetected CPE/CVEs.", dependency.getFileName());
                            LOGGER.warning(msg);
                            LOGGER.fine("Unable to delete temp file");
                        }
                        LOGGER.fine(String.format("Downloading %s", ma.getPomUrl()));
                        Downloader.fetchFile(new URL(ma.getPomUrl()), pomFile);
                        analyzePOM(dependency, pomFile);

                    } catch (DownloadFailedException ex) {
                        final String msg = String.format("Unable to download pom.xml for %s from Central; this could result in undetected CPE/CVEs.", dependency.getFileName());
                        LOGGER.warning(msg);
                    } finally {
//                        if (pomFile != null && !FileUtils.deleteQuietly(pomFile)) {
//                            pomFile.deleteOnExit();
//                        }
                    }
                }

            }
        } catch (IllegalArgumentException iae) {
            LOGGER.info(String.format("invalid sha1-hash on %s", dependency.getFileName()));
        } catch (FileNotFoundException fnfe) {
            LOGGER.fine(String.format("Artifact not found in repository: '%s", dependency.getFileName()));
        } catch (IOException ioe) {
            LOGGER.log(Level.FINE, "Could not connect to Central search", ioe);
            errorFlag = true;
        }
    }

    /**
     * Reads in the pom file and adds elements as evidence to the given dependency.
     *
     * @param dependency the dependency being analyzed
     * @param pomFile the pom file to read
     * @throws AnalysisException is thrown if there is an exception parsing the pom
     */
    protected void analyzePOM(Dependency dependency, File pomFile) throws AnalysisException {
        Model pom = pomUtil.readPom(pomFile);

        String groupid = pom.getGroupId();
        String parentGroupId = null;

        if (pom.getParent() != null) {
            parentGroupId = pom.getParent().getGroupId();
            if ((groupid == null || groupid.isEmpty()) && parentGroupId != null && !parentGroupId.isEmpty()) {
                groupid = parentGroupId;
            }
        }
        if (groupid != null && !groupid.isEmpty()) {
            dependency.getVendorEvidence().addEvidence("pom", "groupid", groupid, Confidence.HIGHEST);
            dependency.getProductEvidence().addEvidence("pom", "groupid", groupid, Confidence.LOW);
            if (parentGroupId != null && !parentGroupId.isEmpty() && !parentGroupId.equals(groupid)) {
                dependency.getVendorEvidence().addEvidence("pom", "parent-groupid", parentGroupId, Confidence.MEDIUM);
                dependency.getProductEvidence().addEvidence("pom", "parent-groupid", parentGroupId, Confidence.LOW);
            }
        }
        String artifactid = pom.getArtifactId();
        String parentArtifactId = null;
        if (pom.getParent() != null) {
            parentArtifactId = pom.getParent().getArtifactId();
            if ((artifactid == null || artifactid.isEmpty()) && parentArtifactId != null && !parentArtifactId.isEmpty()) {
                artifactid = parentArtifactId;
            }
        }
        if (artifactid != null && !artifactid.isEmpty()) {
            if (artifactid.startsWith("org.") || artifactid.startsWith("com.")) {
                artifactid = artifactid.substring(4);
            }
            dependency.getProductEvidence().addEvidence("pom", "artifactid", artifactid, Confidence.HIGHEST);
            dependency.getVendorEvidence().addEvidence("pom", "artifactid", artifactid, Confidence.LOW);
            if (parentArtifactId != null && !parentArtifactId.isEmpty() && !parentArtifactId.equals(artifactid)) {
                dependency.getProductEvidence().addEvidence("pom", "parent-artifactid", parentArtifactId, Confidence.MEDIUM);
                dependency.getVendorEvidence().addEvidence("pom", "parent-artifactid", parentArtifactId, Confidence.LOW);
            }
        }
        //version
        String version = pom.getVersion();
        String parentVersion = null;
        if (pom.getParent() != null) {
            parentVersion = pom.getParent().getVersion();
            if ((version == null || version.isEmpty()) && parentVersion != null && !parentVersion.isEmpty()) {
                version = parentVersion;
            }
        }
        if (version != null && !version.isEmpty()) {
            dependency.getVersionEvidence().addEvidence("pom", "version", version, Confidence.HIGHEST);
            if (parentVersion != null && !parentVersion.isEmpty() && !parentVersion.equals(version)) {
                dependency.getVersionEvidence().addEvidence("pom", "parent-version", version, Confidence.LOW);
            }
        }

        final Organization org = pom.getOrganization();
        if (org != null) {
            final String orgName = org.getName();
            if (orgName != null && !orgName.isEmpty()) {
                dependency.getVendorEvidence().addEvidence("pom", "organization name", orgName, Confidence.HIGH);
            }
        }
        final String pomName = pom.getName();
        if (pomName != null && !pomName.isEmpty()) {
            dependency.getProductEvidence().addEvidence("pom", "name", pomName, Confidence.HIGH);
            dependency.getVendorEvidence().addEvidence("pom", "name", pomName, Confidence.HIGH);
        }

        if (pom.getDescription() != null) {
            final String description = pom.getDescription();
            if (description != null && !description.isEmpty()) {
                JarAnalyzer.addDescription(dependency, description, "pom", "description");
            }
        }
        JarAnalyzer.extractLicense(pom, null, dependency);
    }
}
