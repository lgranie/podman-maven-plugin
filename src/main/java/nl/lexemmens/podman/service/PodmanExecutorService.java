package nl.lexemmens.podman.service;

import nl.lexemmens.podman.command.podman.*;
import nl.lexemmens.podman.config.image.single.SingleImageConfiguration;
import nl.lexemmens.podman.config.podman.PodmanConfiguration;
import nl.lexemmens.podman.executor.CommandExecutorDelegate;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Class that allows very specific execution of Podman related commands.
 */
public class PodmanExecutorService {

    private final Log log;

    private final CommandExecutorDelegate delegate;

    private final PodmanConfiguration podmanConfig;

    /**
     * Constructs a new instance of this class.
     *
     * @param log          Used to access Maven's log system
     * @param podmanConfig Contains Podman specific configuration, such as tlsVerify and podman's root directory
     * @param delegate     A delegate executor that executed the actual command
     */
    public PodmanExecutorService(Log log, PodmanConfiguration podmanConfig, CommandExecutorDelegate delegate) {
        this.log = log;
        this.delegate = delegate;
        this.podmanConfig = podmanConfig;
    }

    /**
     * <p>
     * Implementation of the 'podman build' command.
     * </p>
     * <p>
     * Takes an {@link SingleImageConfiguration} class as input and uses it to retrieve
     * the Containerfile to build, whether caching should be used and the build's output directory
     * </p>
     *
     * @param image The {@link SingleImageConfiguration} containing the configuration of the image to build
     * @return The last line of the build process, usually containing the image hash
     * @throws MojoExecutionException In case the container image could not be built.
     */
    public List<String> build(SingleImageConfiguration image) throws MojoExecutionException {
        PodmanBuildCommand.Builder builder = new PodmanBuildCommand.Builder(log, podmanConfig, delegate)
                .setFormat(image.getBuild().getFormat().getValue())
                .setContainerFile(image.getBuild().getTargetContainerFile())
                .setNoCache(image.getBuild().isNoCache());

        if (Boolean.TRUE == image.getBuild().getSquash()) {
            builder = builder.setSquash();
        }

        if (Boolean.TRUE == image.getBuild().getSquashAll()) {
            builder = builder.setSquashAll();
        }

        if (image.getBuild().getLayers() != null) {
            builder = builder.setLayers(image.getBuild().getLayers());
        }

        Optional<Boolean> pullOptional = image.getBuild().getPull();
        if (pullOptional.isPresent()) {
            builder = builder.setPull(pullOptional.get());
        }

        Optional<Boolean> pullAlwaysOptional = image.getBuild().getPullAlways();
        if (pullAlwaysOptional.isPresent()) {
            builder = builder.setPullAllways(pullAlwaysOptional.get());
        }

        Optional<String> platform = image.getBuild().getPlatform();
        if (platform.isPresent()) {
            builder = builder.setPlatform(platform.get());
        }
        
        builder.addBuildArgs(image.getBuild().getArgs());

        return builder.build().execute();
    }

    /**
     * <p>
     * Implementation of the 'podman tag' command.
     * </p>
     *
     * @param imageHash     The image hash as generated by the {@link #build(SingleImageConfiguration)} method
     * @param fullImageName The full name of the image. This will be the target name
     * @throws MojoExecutionException In case the container image could not be tagged.
     */
    public void tag(String imageHash, String fullImageName) throws MojoExecutionException {
        new PodmanTagCommand.Builder(log, podmanConfig, delegate)
                .setImageHash(imageHash)
                .setFullImageName(fullImageName)
                .build()
                .execute();
    }

    /**
     * <p>
     * Implementation of the 'podman save' command.
     * </p>
     * <p>
     * Note: This is not an export. The result of the save command is a tar ball containing all layers
     * as separate folders
     * </p>
     *
     * @param archiveName   The target name of the archive, where the image will be saved into.
     * @param fullImageName The image to save
     * @throws MojoExecutionException In case the container image could not be saved.
     */
    public void save(String archiveName, String fullImageName) throws MojoExecutionException {
        new PodmanSaveCommand.Builder(log, podmanConfig, delegate)
                .setArchiveName(archiveName)
                .setFullImageName(fullImageName)
                .build()
                .execute();
    }

    /**
     * <p>
     * Implementation of the 'podman push' command.
     * </p>
     *
     * @param fullImageName The full name of the image including the registry
     * @throws MojoExecutionException In case the container image could not be pushed.
     */
    public void push(String fullImageName) throws MojoExecutionException {
        new PodmanPushCommand.Builder(log, podmanConfig, delegate)
                .setFullImageName(fullImageName)
                .build()
                .execute();
    }

    /**
     * <p>
     * Implementation of the 'podman login' command.
     * </p>
     * <p>
     * This command is used to login to a specific registry with a specific username and password
     * </p>
     *
     * @param registry The registry to logon to
     * @param username The username to use
     * @param password The password to use
     * @throws MojoExecutionException In case the login fails. The Exception does not contain a recognisable password.
     */
    public void login(String registry, String username, String password) throws MojoExecutionException {
        try {
            new PodmanLoginCommand.Builder(log, podmanConfig, delegate)
                    .setRegistry(registry)
                    .setUsername(username)
                    .setPassword(password)
                    .build()
                    .execute();
        } catch (MojoExecutionException e) {
            // When the command fails, the whole command is put in the error message, possibly exposing passwords.
            // Therefore we catch the exception, remove the password and throw a new exception with an updated message.
            String message = e.getMessage().replaceAll(String.format("-p[,=]+%s", Pattern.quote(password)), "-p=**********");
            log.error(message);
            throw new MojoExecutionException(message);
        }
    }

    /**
     * <p>
     * Implementation of the 'podman version' command
     * </p>
     *
     * @throws MojoExecutionException In case printing the information fails
     */
    public void version() throws MojoExecutionException {
        new PodmanVersionCommand.Builder(log, podmanConfig, delegate)
                .build()
                .execute();
    }

    /**
     * <p>
     * Implementation of the 'podman rmi' command.
     * </p>
     *
     * <p>
     * Removes an image from the local registry
     * </p>
     *
     * @param fullImageName The full name of the image to remove from the local registry
     * @throws MojoExecutionException In case the container image could not be removed.
     */
    public void removeLocalImage(String fullImageName) throws MojoExecutionException {
        new PodmanRmiCommand.Builder(log, podmanConfig, delegate)
                .setFullImageName(fullImageName)
                .build()
                .execute();
    }
}
