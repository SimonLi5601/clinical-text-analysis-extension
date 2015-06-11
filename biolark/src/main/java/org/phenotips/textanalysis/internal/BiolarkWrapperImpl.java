/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.textanalysis.internal;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.environment.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import au.edu.uq.eresearch.biolark.cr.Annotation;
import au.edu.uq.eresearch.biolark.cr.BioLarK_CR;

/**
 * Wrapper component for BioLark phenotype annotation library.
 *
 * @version $Id$
 */
@Component
public class BiolarkWrapperImpl implements BiolarkWrapper, Initializable
{
    /** Relative path to the directory containing biolark resources. */
    public static final String ROOT_DIRECTORY = "resources/BioLark-CR/";

    /** Name of biolark properties file. */
    public static final String PROPERTIES_FILENAME = "cr.properties";

    /** Name of directory for biolark generated input/output files, which will be ignored. */
    public static final String IO_FILENAME = "empty";

    /** Name of directory for biolark generated temporary files. */
    public static final String TA_TMP = "ta_tmp";

    /** Url of biolark resources archive. */
    public static final String RESOURCE_FILES_URL =
        "http://nexus.cs.toronto.edu/nexus/service/local/repositories/externals/"
            + "content/org/biolark/biolark-resources/0.1a/biolark-resources-0.1a.jar";

    @Inject
    private Environment environment;

    private BioLarK_CR biolark;

    @Override
    public void initialize() throws InitializationException
    {
        String propertiesPath;
        try {
            propertiesPath = generateBiolarkResources();
            this.biolark = new BioLarK_CR(propertiesPath);
        } catch (IOException e) {
            throw new InitializationException(e.getMessage());
        }
    }

    @Override
    public List<Annotation> annotatePlain(String text, boolean longestMatch)
    {
        return this.biolark.annotate_plain(text, longestMatch);
    }

    /**
     * Creates a properties file to be used by the biolark annotation plugin.
     *
     * @return path to generated biolark properties file
     * @throws IOException in case of error in reading/writing property files
     * @throws InitializationException if building one of biolark's dependencies fails
     */
    public String generateBiolarkResources() throws IOException, InitializationException
    {
        final File biolarkRoot = new File(this.environment.getPermanentDirectory(), BiolarkWrapperImpl.ROOT_DIRECTORY);
        final File biolarkProperties = new File(biolarkRoot, BiolarkWrapperImpl.PROPERTIES_FILENAME);
        final File emptyDir = new File(biolarkRoot, BiolarkWrapperImpl.IO_FILENAME);
        final File taTmpDir = new File(biolarkRoot, BiolarkWrapperImpl.TA_TMP);

        // Check for existing biolark files
        if (biolarkProperties.exists() && !biolarkProperties.isDirectory()) {
            return biolarkProperties.getAbsolutePath();
        }

        // Create Biolark work directories
        biolarkRoot.mkdirs();
        emptyDir.mkdirs();
        taTmpDir.mkdirs();

        // Download and extract biolark files
        final String pathToArchive =
            new File(this.environment.getTemporaryDirectory(), "biolark_resources.jar").getAbsolutePath();
        File resources = BiolarkFileUtils.downloadFile(pathToArchive, BiolarkWrapperImpl.RESOURCE_FILES_URL);
        BiolarkFileUtils.extractArchive(resources, biolarkRoot);

        // build extracted biolark dependencies
        File dependencies = new File(biolarkRoot, "resources");
        for (File dir : dependencies.listFiles()) {
            if (dir.isDirectory() && new File(dir, "Makefile").exists()) {
                try {
                    BiolarkFileUtils.make(dir, Runtime.getRuntime());
                } catch (BuildException e) {
                    throw new InitializationException(e.getMessage());
                }
            }
        }

        // Create properties file
        final FileWriter bw = new FileWriter(biolarkProperties);
        bw.write("logLevel=INFO\n");
        bw.write("longestMatch=FALSE\n");
        bw.write("outputFormat=text\n");
        bw.append("path=").append(biolarkRoot.getAbsolutePath());
        bw.append(System.lineSeparator());
        bw.append("inputFolder=").append(emptyDir.getAbsolutePath());
        bw.append(System.lineSeparator());
        bw.append("outputFolder=").append(emptyDir.getAbsolutePath());
        bw.append(System.lineSeparator());
        bw.close();

        return biolarkProperties.getAbsolutePath();
    }

}
