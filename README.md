![Apache MINA SSHD](https://mina.apache.org/staticresources/images/header-sshd.png "Apache MINA SSHD")
# Apache MINA SSHD

Apache SSHD is a 100% pure java library to support the SSH protocols on both the client and server side. This library can leverage [Apache MINA](http://mina.apache.org), a scalable and high performance asynchronous IO library. SSHD does not really aim at being a replacement for the SSH client or SSH server from Unix operating systems, but rather provides support for Java based applications requiring SSH support.

# Core requirements

* Java 8+ (as of version 1.3)

* [Slf4j](http://www.slf4j.org/)

The code only requires the core abstract [slf4j-api](https://mvnrepository.com/artifact/org.slf4j/slf4j-api) module. The actual implementation of the logging API can be selected from the many existing adaptors.

* [Bouncy Castle](https://www.bouncycastle.org/)

Required only for reading/writing keys from/to PEM files or for special keys/ciphers/etc. that are not part of the standard [Java Cryptography Extension](https://en.wikipedia.org/wiki/Java_Cryptography_Extension). See [Java Cryptography Architecture (JCA) Reference Guide](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html) for key classes and explanations as to how _Bouncy Castle_ is plugged in (other security providers).

* [MINA core](https://mina.apache.org/mina-project/)

Optional dependency to enable choosing between NIO asynchronous sockets (the default - for improved performance), and "legacy" sockets. See `IoServiceFactoryFactory` implementations and specifically the `DefaultIoServiceFactoryFactory` for the available options and how it can be configured to select among them.

# Set up an SSH client in 5 minutes

SSHD is designed to easily allow setting up and using an SSH client in a few simple steps. The client needs to be configured and then started before it can be used to connect to an SSH server. There are a few simple steps for creating a client instance - for more more details refer to the `SshClient` class.

## Creating an instance of the `SshClient` class

This is simply done by calling

```java

    SshClient client = SshClient.setupDefaultClient();

```

The call will create an instance with a default configuration suitable for most use cases - including ciphers, compression, MACs, key exchanges, signatures, etc... If your code requires some special configuration, you can look at the code for `setupDefaultClient` and `checkConfig` as a reference for available options and configure the SSH client the way you need.

## Set up client side security

The SSH client contains some security related configuration that one needs to consider

### `ServerKeyVerifier`

`client.setServerKeyVerifier(...);` sets up the server key verifier. As part of the SSH connection initialization protocol, the server proves its "identity" by presenting a public key. The client can examine the key (e.g., present it to the user via some UI) and decide whether to trust the server and continue with the connection setup. By default the client is initialized with an `AcceptAllServerKeyVerifier` that simply logs a warning that an un-verified server key was accepted. There are other out-of-the-box verifiers available in the code:

* `RejectAllServerKeyVerifier` - rejects all server key - usually used in tests or as a fallback verifier if none of it predecesors validated the server key


* `RequiredServerKeyVerifier` - accepts only **one** specific server key (similar to certificate pinning for SSL)


* `KnownHostsServerKeyVerifier` - uses the [known_hosts](https://en.wikibooks.org/wiki/OpenSSH/Client_Configuration_Files#Public_Keys_from_other_Hosts_.E2.80.93_.7E.2F.ssh.2Fknown_hosts) file to validate the server key. One can use this class + some existing code to **update** the file when new servers are detected and their keys are accepted.


Of course, one can implement the verifier in whatever other manner is suitable for the specific code needs.

### ClientIdentityLoader/KeyPairProvider

One can set up the public/private keys to be used in case a password-less authentication is needed. By default, the client is configured to automatically detect and use the identity files residing in the user's *~/.ssh* folder (e.g., *id_rsa*, *id_ecdsa*) and present them as part of the authentication process. **Note:** if the identity files are encrypted via a password, one must configure a `FilePasswordProvider` so that the code can decrypt them before using and presenting them to the server as part of the authentication process. Reading key files in PEM format (including encrypted ones) requires that the [Bouncy Castle](https://www.bouncycastle.org/) supporting artifacts be available in the code's classpath.

### UserInteraction

This interface is required for full support of `keyboard-interactive` authentication protocol as described in [RFC 4256](https://www.ietf.org/rfc/rfc4256.txt). The client can handle a simple password request from the server, but if more complex challenge-response interaction is required, then this interface must be provided - including support for `SSH_MSG_USERAUTH_PASSWD_CHANGEREQ` as described in [RFC 4252 section 8](https://www.ietf.org/rfc/rfc4252.txt).

While RFC-4256 support is the primary purpose of this interface, it can also be used to retrieve the server's welcome banner as described in [RFC 4252 section 5.4](https://www.ietf.org/rfc/rfc4252.txt) as well as its initial identification string as described in [RFC 4253 section 4.2](https://tools.ietf.org/html/rfc4253#section-4.2).

## Using the `SshClient` to connect to a server

Once the `SshClient` instance is properly configured it needs to be `start()`-ed in order to connect to a server. **Note:** one can use a single `SshClient` instance to connnect to multiple server as well as modifying the default configuration (ciphers, MACs, keys, etc.) on a per-session manner (see more in the *Advanced usage* section). Furthermore, one can change almost any configured `SshClient` parameter - although its influence on currently established sessions depends on the actual changed configuration. Here is how a typical usage would look like

```java

    SshClient client = SshClient.setupDefaultClient();
    // override any default configuration...
    client.setSomeConfiguration(...);
    client.setOtherConfiguration(...);
    client.start();

        // using the client for multiple sessions...
        try (ClientSession session = client.connect(user, host, port).verify(...timeout...).getSession()) {
            session.addPasswordIdentity(...password..); // for password-based authentication
            // or
            session.addPublicKeyIdentity(...key-pair...); // for password-less authentication
            // Note: can add BOTH password AND public key identities - depends on the client/server security setup

            session.auth().verify(...timeout...);
            // start using the session to run commands, do SCP/SFTP, create local/remote port forwarding, etc...
        }

        // NOTE: this is just an example - one can open multiple concurrent sessions using the same client.
        //      No need to close the previous session before establishing a new one
        try (ClientSession anotherSession = client.connect(otherUser, otherHost, port).verify(...timeout...).getSession()) {
            anotherSession.addPasswordIdentity(...password..); // for password-based authentication
            anotherSession.addPublicKeyIdentity(...key-pair...); // for password-less authentication
            anotherSession.auth().verify(...timeout...);
            // start using the session to run commands, do SCP/SFTP, create local/remote port forwarding, etc...
        }

    // exiting in an orderly fashion once the code no longer needs to establish SSH session
    // NOTE: this can/should be done when the application exits.
    client.stop();

```

# Embedding an SSHD server instance in 5 minutes

SSHD is designed to be easily embedded in your application as an SSH server. The embedded SSH server needs to be configured before it can be started. Essentially, there are a few simple steps for creating the server - for more details refer to the `SshServer` class.

## Creating an instance of the `SshServer` class

Creating an instance of `SshServer` is as simple as creating a new object

```java

    SshServer sshd = SshServer.setUpDefaultServer();

```

It will configure the server with sensible defaults for ciphers, macs, key exchange algorithm, etc... If you want a different behavior, you can look at the code of the `setUpDefaultServer` as well as `checkConfig` methods as a reference for available options and configure the SSH server the way you need.

## Configuring the server instance

There are a few things that need to be configured on the server before being able to actually use it:

* Port - `sshd.setPort(22);` - sets the listen port for the server instance. If not set explicitly then a **random** free port is selected by the O/S. In any case, once the server is `start()`-ed one can query the instance as to the assigned port via `sshd.getPort()`.


In this context, the listen bind address can also be specified explicitly via `sshd.setHost(...some IP address...)` that causes the server to bind to a specific network address rather than all addresses (the default). Using "0.0.0.0" as the bind address is also tantamount to binding to all addresses.


* `KeyPairProvider` - `sshd.setKeyPairProvider(...);` - sets the host's private keys used for key exchange with clients as well as representing the host's "identities". There are several choices - one can load keys from standard PEM files or generate them in the code.  It's usually a good idea to save generated keys, so that if the SSHD server is restarted, the same keys will be used to authenticate the server and avoid the warning the clients might get if the host keys are modified. **Note**: loading or saving key files in PEM format requires  that the [Bouncy Castle](https://www.bouncycastle.org/) supporting artifacts be available in the code's classpath.


* `ShellFactory` - That's the part you will usually have to write to customize the SSHD server. The shell factory will be used to create a new shell each time a user logs in and wants to run an interactive shelll. SSHD provides a simple implementation that you can use if you want. This implementation will create a process and delegate everything to it, so it's mostly useful to launch the OS native shell. E.g.,


```java

    sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/sh", "-i", "-l" }));

```


There is an out-of-the-box `InteractiveProcessShellFactory` that detects the O/S and spawns the relevant shell. Note that the `ShellFactory` is not required. If none is configured, any request for an interactive shell will be denied to clients.


* `CommandFactory` - The `CommandFactory` provides the ability to run a **single** direct command at a time instead of an interactive session (it also uses a **different** channel type than shells). It can be used **in addition** to the `ShellFactory`.


SSHD provides a `CommandFactory` to support SCP that can be configured in the following way:


```java

    sshd.setCommandFactory(new ScpCommandFactory());

```

You can also use the `ScpCommandFactory` on top of your own `CommandFactory` by placing your command factory as a **delegate** of the `ScpCommandFactory`. The `ScpCommandFactory` will intercept SCP commands and execute them by itself, while passing all other commands to (your) delegate `CommandFactory`


```java

    sshd.setCommandFactory(new ScpCommandFactory(myCommandFactory));

```

Note that using a `CommandFactory` is also **optional**. If none is configured, any direct command sent by clients will be rejected.

## Server side security setup

The SSHD server security layer has to be customized to suit your needs. This layer is pluggable and uses the following interfaces:

* `PasswordAuthenticator` for password based authentication - [RFC 4252 section 8](https://www.ietf.org/rfc/rfc4252.txt)
* `PublickeyAuthenticator` for key based authentication - [RFC 4252 section 7](https://www.ietf.org/rfc/rfc4252.txt)
* `HostBasedAuthenticator` for host based authentication - [RFC 4252 section 9](https://www.ietf.org/rfc/rfc4252.txt)
* `KeyboardInteractiveAuthenticator` for user interactive authentication - [RFC 4256](https://www.ietf.org/rfc/rfc4256.txt)


These custom classes can be configured on the SSHD server using the respective setter methods:


```java

    sshd.setPasswordAuthenticator(new MyPasswordAuthenticator());
    sshd.setPublickeyAuthenticator(new MyPublickeyAuthenticator());
    sshd.setKeyboardInteractiveAuthenticator(new MyKeyboardInteractiveAuthenticator());
    ...etc...

```

Several useful implementations are available that can be used as-is or extended in order to provide some custom behavior. In any case, the default initializations are:

* `DefaultAuthorizedKeysAuthenticator` - uses the _authorized_keys_ file the same way as the SSH daemon does
* `DefaultKeyboardInteractiveAuthenticator` - for password-based or interactive authentication. **Note:** this authenticator requires a `PasswordAuthenticator` to be configured since it delegates some of the functionality to it.

## Configuring ciphers, macs, digest...

SSH supports pluggable factories to define various configuration parts such as ciphers, digests, key exchange, etc...
The list of supported implementations can be changed to suit your needs, or you can also implement your own factories.

Configuring supported ciphers can be done with the following code:

```java

    sshd.setCipherFactories(Arrays.asList(BuiltinCiphers.aes256ctr, BuiltinCiphers.aes192ctr, BuiltinCiphers.aes128ctr));

```

You can configure other security components using builtin factories the same way.

## Starting the Server

Once we have configured the server, one need only call `sshd.start();`. **Note**: once the server is started, all of the configurations (except the port) can still be *overridden* while the server is running (caveat emptor). In such cases, only **new** clients that connect to the server after the change will be affected - with the exception of the negotiation options (keys, macs, ciphers, etc...) which take effect the next time keys are re-exchanged, that can affect live sessions and not only new ones.

# SSH functionality breakdown

## `FileSystemFactory` usage

This interface is used to provide "file"-related services - e.g., SCP and SFTP - although it can be used for remote command execution
as well (see the section about commands and the `Aware` interfaces). The default implementation is a `NativeFileSystemFactory`
that simply exposes the [FileSystems.getDefault()](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystems.html#getDefault)
result. However, for "sandboxed" implementations one can use the `VirtualFileSystemFactory`. This implementation provides a way for
deciding what is the logged-in user's file system view and then use a `RootedFileSystemProvider` in order to provide a "sandboxed"
file system where the logged-in user can access only the files under the specified root and no others.

```java

    SshServer sshd = SshServer.setupDefaultServer();
    sshd.setFileSystemFactory(new VirtualFileSystemFactory() {
        @Override
        protected Path computeRootDir(Session session) throws IOException  {
            String username = session.getUsername(); // or any other session related parameter
            Path path = resolveUserHome(username);
            return path;
        }
    });

```

The usage of a `FileSystemFactory` is not limited though to the server only - the `ScpClient` implementation also uses
it in order to retrieve the *local* path for upload/download-ing files/folders. This means that the client side can also
be tailored to present different views for different clients


## `ExecutorService`-s

The framework requires from time to time spawning some threads in order to function correctly - e.g., commands, SFTP subsystem,
port forwarding (among others) require such support. By default, the framework will allocate an [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) for each specific purpose and then shut it down when the module has completed its work - e.g., session
was closed. Users may provide their own `ExecutorService`(s) instead of the internally auto-allocated ones - e.g., in
order to control the max. spawned threads, stack size, track threads, etc... If this is done, then one must also provide
the `shutdownOnExit` value indicating to the overridden module whether to shut down the service once it is no longer necessary.

```java

    /*
     * An example for SFTP - there are other such locations. By default,
     * the SftpSubsystem implementation creates a single-threaded executor
     * for each session, uses it to spawn the SFTP command handler and shuts
     * it down when the command is destroyed
     */
    SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder()
        .withExecutorService(mySuperDuperExecutorService)
        .withShutdownOnExit(false)  // I will take care of shutting it down
        .build();
    SshServer sshd = SshServer.setupDefaultServer();
    sshd.setSubsystemFactories(Collections.<NamedFactory<Command>>singletonList(factory));

```

## Remote command execution

All command execution - be it shell or single command - boils down to a `Command` instance being created, initialized and then
started. In this context, it is **crucial** to notice that the command's `start()` method implementation **must spawn a new thread** - even
for the simplest or most trivial command. Any attempt to communicate via the established session will most likely **fail** since
the packets processing thread may be blocked by this call. **Note:** one might get away with executing some command in the
context of the thread that called the `start()` method, but it is **extremely dangerous** and should not be attempted.

The command execution code can communicate with the peer client via the input/output/error streams that are provided as
part of the command initialization process. Once the command is done, it should call the `ExitCallback#onExit` method to indicate
that it has finished. The framework will then take care of propagating the exit code, closing the session and (eventually) `destroy()`-ing
the command. **Note**: the command may not assume that it is done until its `destroy()` method is called - i.e., it should not
release or null-ify any of its internal state even if `onExit()` was called.

Upon calling the `onExit` method the code sends an [SSH_MSG_CHANNEL_EOF](https://tools.ietf.org/html/rfc4254#section-5.3) message, and the provided result status code
is sent as an `exit-status` message as described in [RFC4254 - section 6.10](https://tools.ietf.org/html/rfc4254#section-6.10).
The provided message is simply logged at DEBUG level.

```java

    // A simple command implementation example
    class MyCommand implements Command, Runnable {
        private InputStream in;
        private OutputStream out, err;
        private ExitCallback callback;

        public MyCommand() {
            super();
        }

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(Environment env) throws IOException {
            spawnHandlerThread(this);
        }

        @Override
        public void run() {
            while(true) {
                try {
                    String cmd = readCommand(in);
                    if ("exit".equals(cmd)) {
                        break;
                    }

                    handleCommand(cmd, out);
                } catch (Exception e) {
                    writeError(err, e);
                    onExit(-1, e.getMessage());
                    return;
            }

            callback.onExit(0);
        }
    }
```

### `Aware` interfaces

Once created, the `Command` instance is checked to see if it implements one of the `Aware` interfaces that enables
injecting some dynamic data before the command is `start()`-ed.

* `SessionAware` - Injects the `Session` instance through which the command request was received.

* `ChannelSessionAware` - Injects the `ChannelSession` instance through which the command request was received.

* `FileSystemAware` - Injects the result of consulting the `FileSystemFactory` as to the [FileSystem](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html)
associated with this command.


## SCP

Besides the `ScpTransferEventListener`, the SCP module also uses a `ScpFileOpener` instance in order to access
the local files - client or server-side. The default implementation simply opens an [InputStream](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html)
or [OutputStream](https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html) on the requested local path. However,
the user may replace it and intercept the calls - e.g., for logging, for wrapping/filtering the streams, etc... **Note:**
due to SCP protocol limitations one cannot change the **size** of the input/output since it is passed as part of the command
**before** the file opener is invoked - so there are a few limitations on what one can do within this interface implementation.


## SFTP

In addition to the `SftpEventListener` there are a few more SFTP-related special interfaces and modules.


### Version selection via `SftpVersionSelector`


The SFTP subsystem code supports versions 3-6 (inclusive), and by default attempts to negotiate the **highest**
possible one - on both client and server code. The user can intervene and force a specific version or a narrower
range.


```java

    SftpVersionSelector myVersionSelector = new SftpVersionSelector() {
        @Override
        public int selectVersion(ClientSession session, int current, List<Integer> available) {
            int selectedVersion = ...run some logic to decide...;
            return selectedVersion;
        }
    };

    try (ClientSession session = client.connect(user, host, port).verify(timeout).getSession()) {
        session.addPasswordIdentity(password);
        session.auth.verify(timeout);

        try (SftpClient sftp = session.createSftpClient(myVersionSelector)) {
            ... do SFTP related stuff...
        }
    }

```

On the server side, version selection restriction is more complex - please remember that the **client** chooses
the version, and all we can do at the server is require a **specific** version via the `SftpSubsystem#SFTP_VERSION`
configuration key. For more advanced restrictions on needs to sub-class `SftpSubSystem` and provide a non-default
`SftpSubsystemFactory` that uses the sub-classed code.


### Using `SftpFileSystemProvider` to create an `SftpFileSystem`


The code automatically registers the `SftpFileSystemProvider` as the handler for `sftp://` URL(s). Such URLs are
interpreted as remote file locations and automatically exposed to the user as [Path](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Path.html)
objects. In effect, this allows the code to "mount" a remote directory via SFTP and treat it as if it were local using
standard [java.nio](https://docs.oracle.com/javase/8/docs/api/java/nio/package-frame.html) calls like any "ordinary" file
system.

```java

    // Direct URI
    Path remotePath = Paths.get(new URI("sftp://user:password@host/some/remote/path"));

    // "Mounting" a file system
    URI uri = SftpFileSystemProvider.createFileSystemURI(host, port, username, password);
    FileSystem fs = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
    Path remotePath = fs.getPath("/some/remote/path");

    // Full programmatic control
    SshClient client = ...setup and start the SshClient instance...
    SftpFileSystemProvider provider = new SftpFileSystemProvider(client);
    URI uri = SftpFileSystemProvider.createFileSystemURI(host, port, username, password);
    FileSystem fs = provider.newFileSystem(uri, Collections.<String, Object>emptyMap());
    Path remotePath = fs.getPath("/some/remote/path");

```

 The obtained `Path` instance can be used in exactly the same way as any other "regular" one:


 ```java

    try (InputStream input = Files.newInputStream(remotePath)) {
        ...read from remote file...
    }

    try (DirectoryStream<Path> ds = Files.newDirectoryStream(remoteDir)) {
        for (Path remoteFile : ds) {
            if (Files.isRegularFile(remoteFile)) {
                System.out.println("Delete " + remoteFile + " size=" + Files.size(remoteFile));
                Files.delete(remoteFile);
            } else if (Files.isDirectory(remoteFile)) {
                System.out.println(remoteFile + " - directory");
            }
        }
    }
```

#### Configuring the `SftpFileSystemProvider`

When "mounting" a new file system one can provide configuration parameters using either the
environment map in the [FileSystems#newFileSystem](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystems.html#newFileSystem)
method or via the URI query parameters. See the `SftpFileSystemProvider` for the available
configuration keys and values.


```java

    // Using explicit parameters
    Map<String, Object> params = new HashMap<>();
    params.put("param1", value1);
    params.put("param2", value2);
    ...etc...

    URI uri = SftpFileSystemProvider.createFileSystemURI(host, port, username, password);
    FileSystem fs = FileSystems.newFileSystem(uri, params);
    Path remotePath = fs.getPath("/some/remote/path");

    // Using URI parameters
    Path remotePath = Paths.get(new URI("sftp://user:password@host/some/remote/path?param1=value1&param2=value2..."));

```

**Note**: if **both** options are used then the URI parameters **override** the environment ones


```java

    Map<String, Object> params = new HashMap<>();
    params.put("param1", value1);
    params.put("param2", value2);

    // The value of 'param1' is overridden in the URI
    FileSystem fs = FileSystems.newFileSystem(new URI("sftp://user:password@host/some/remote/path?param1=otherValue1", params);
    Path remotePath = fs.getPath("/some/remote/path");

```

#### Tracking accessed location via `SftpFileSystemAccessor`

One can override the default `SftpFileSystemAccessor` and thus be able to track all opened files and folders
throughout the SFTP server subsystem code. The accessor is registered/overwritten in via the `SftpSubSystemFactory`:

```java

    SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder()
        .withFileSystemAccessor(new MySftpFileSystemAccessor())
        .build();
    server.setSubsystemFactories(Collections.singletonList(factory));

```


### Supported SFTP extensions

Both client and server support several of the SFTP extensions specified in various drafts:

* `supported` - [DRAFT 05 - section 4.4](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-05.tx)
* `supported2` - [DRAFT 13 section 5.4](https://tools.ietf.org/html/draft-ietf-secsh-filexfer-13#page-10)
* `versions` - [DRAFT 09 Section 4.6](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-09.txt)
* `vendor-id` - [DRAFT 09 - section 4.4](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-09.txt)
* `acl-supported` - [DRAFT 11 - section 5.4](https://tools.ietf.org/html/draft-ietf-secsh-filexfer-11)
* `newline` - [DRAFT 09 Section 4.3](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-09.txt)
* `md5-hash`, `md5-hash-handle` - [DRAFT 09 - section 9.1.1](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-09.txt)
* `check-file-handle`, `check-file-name` - [DRAFT 09 - section 9.1.2](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-09.txt)
* `copy-file`, `copy-data` - [DRAFT 00 - sections 6, 7](http://tools.ietf.org/id/draft-ietf-secsh-filexfer-extensions-00.txt)
* `space-available` - [DRAFT 09 - section 9.3](http://tools.ietf.org/wg/secsh/draft-ietf-secsh-filexfer/draft-ietf-secsh-filexfer-09.txt)

Furthermore several [OpenSSH SFTP extensions](https://github.com/openssh/openssh-portable/blob/master/PROTOCOL) are also supported:

* `fsync@openssh.com`
* `fstatvfs@openssh.com`
* `hardlink@openssh.com`
* `posix-rename@openssh.com`
* `statvfs@openssh.com`


On the server side, the reported standard extensions are configured via the `SftpSubsystem.CLIENT_EXTENSIONS_PROP` configuration key, and the _OpenSSH_ ones via the `SftpSubsystem.OPENSSH_EXTENSIONS_PROP`.

On the client side, all the supported extensions are classes that implement `SftpClientExtension`. These classes can be used to query the client whether the remote server supports the specific extension and then obtain a parser for its contents. Users can easily add support for more extensions in a similar manner as the existing ones by implementing an appropriate `ExtensionParser` and then registring it at the `ParserUtils` - see the existing ones for details how this can be achieved.


```java

    // properietary/special extension parser
    ParserUtils.registerExtension(new MySpecialExtension());

    try (ClientSession session = client.connect(username, host, port).verify(timeout).getSession()) {
        session.addPasswordIdentity(password);
        session.auth().verify(timeout);

        try (SftpClient sftp = session.createSftpClient()) {
            Map<String, byte[]> extensions = sftp.getServerExtensions();
            // Key=extension name, value=registered parser instance
            Map<String, ?> data = ParserUtils.parse(extensions);
            for (Map.Entry<String, ?> de : data.entrySet()) {
                String extName = de.getKey();
                Object extValue = de.getValue();
                if (SftpConstants.EXT_ACL_SUPPORTED.equalsIgnoreCase(extName)) {
                    AclCapabilities capabilities = (AclCapabilities) extValue;
                    ...see what other information can be gleaned from it...
                } else if ((SftpConstants.EXT_VERSIONS.equalsIgnoreCase(extName)) {
                    Versions versions = (Versions) extValue;
                    ...see what other information can be gleaned from it...
                } else if ("my-special-extension".equalsIgnoreCase(extName)) {
                    MySpecialExtension special = (MySpecialExtension) extValue;
                    ...see what other information can be gleaned from it...
                } // ...etc....
            }
        }
    }

```

One can skip all the conditional code if a specific known extension is required:


```java
    try (ClientSession session = client.connect(username, host, port).verify(timeout).getSession()) {
        session.addPasswordIdentity(password);
        session.auth().verify(timeout);

        try (SftpClient sftp = session.createSftpClient()) {
            // Returns null if extension is not supported by remote server
            SpaceAvailableExtension space = sftp.getExtension(SpaceAvailableExtension.class);
            if (space != null) {
                ...use it...
            }
        }
    }
```

## Port forwarding

### Standard port forwarding

Port forwarding as specified in [RFC 4254 - section 7](https://tools.ietf.org/html/rfc4254#section-7) is fully supported by the client and server. From the client side, this capability is exposed via the `start/stopLocal/RemotePortForwarding` method. The key player in this capability is the configured `ForwardingFilter` that controls this feature - on **both** sides - client and server. By default, this capability is **disabled** - i.e., the user must provide an implementation and call the appropriate `setTcpipForwardingFilter` method on the client/server.

The code contains 2 simple implementations - an accept-all and a reject-all one that can be used for these trivial policies. **Note:** setting a _null_ filter is equivalent to rejecting all such attempts.

### SOCKS

The code implements a [SOCKS](https://en.wikipedia.org/wiki/SOCKS) proxy for versions 4 and 5. The proxy capability is invoked via the `start/stopDynamicPortForwarding` methods.

### Proxy agent

The code provides to some extent an SSH proxy agent via the available `SshAgentFactory` implementations. As of latest version both [ Secure Shell Authentication Agent Protocol Draft 02](https://tools.ietf.org/html/draft-ietf-secsh-agent-02) and its [OpenSSH](https://www.libssh.org/features/) equivalent are supported.


# Advanced configuration and interaction

## Properties and inheritance model
The code's behavior is highly customizable not only via non-default implementations of interfaces but also as far as the **parameters** that govern its behavior - e.g., timeouts, min./max. values, allocated memory size, etc... All the customization related code flow implements a **hierarchical** `PropertyResolver` inheritance model where the "closest" entity is consulted first, and then its "owner", and so on until the required value is found. If the entire hierarchy yielded no specific result, then some pre-configured default is used. E.g., if a channel requires some parameter in order to decide how to behave, then the following configuration hierarchy is consulted:

* The channel-specific configuration
* The "owning" session configuration
* The "owning" client/server instance configuration
* The system properties - **Note:** any configuration value required by the code can be provided via a system property bearing the `org.apache.sshd.config` prefix - see `SyspropsMapWrapper` for the implementation details.


### Using the inheritance model for fine-grained/targeted configuration

As previously mentioned, this hierarchical lookup model is not limited to "simple" configuration values (strings, integers, etc.), but used also for **interfaces/implementations** such as cipher/MAC/compression/authentication/etc. factories - the exception being that the system properties are not consulted in such a case. This code behavior provides highly customizable fine-grained/targeted control of the code's behavior - e.g., one could impose usage of specific ciphers/authentication methods/etc. or present different public key "identities"/welcome banner behavior/etc., based on address, username or whatever other decision parameter is deemed relevant by the user's code. This can be done on __both__ sides of the connection - client or server. E.g., the client could present different keys based on the server's address/identity string/welcome banner, or the server could accept only specific types of authentication methods based on the client's address/username/etc... This can be done in conjuction with the usage of the various `EventListener`-s provided by the code (see below).

One of the code locations where this behavior can be leveraged is when the server provides __file-based__ services (SCP, SFTP) in order to provide a different/limited view of the available files based on the username - see the section dealing with `FileSystemFactory`-ies.

## Welcome banner configuration

According to [RFC 4252 - section 5.4](https://tools.ietf.org/html/rfc4252#section-5.4) the server may send a welcome banner message during the authentication process. Both the message contents and the phase at which it is sent can be configured/customized.

### Welcome banner content customization

The welcome banner contents are controlled by the `ServerAuthenticationManager.WELCOME_BANNER` configuration key - there are several possible values for this key:

* A simple string - in which case its contents are the welcome banner.


* A file [URI](https://docs.oracle.com/javase/8/docs/api/java/net/URI.html) - or a string starting with `"file:/"` followed by the file path - see below.


* A [URL](https://docs.oracle.com/javase/8/docs/api/java/net/URL.html) - or a string contaning "://" - in which case the [URL#openStream()](https://docs.oracle.com/javase/8/docs/api/java/net/URL.html#openStream) method is invoked and its contents are read.


* A [File](https://docs.oracle.com/javase/8/docs/api/java/io/File.html) or a [Path](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Path.html) - in this case, the file's contents are __re-loaded__ every time it is required and sent as the banner contents.


* The special value `ServerAuthenticationManager.AUTO_WELCOME_BANNER_VALUE` which generates a combined "random art" of all the server's keys as described in `Perrig A.` and `Song D.`-s article [Hash Visualization: a New Technique to improve Real-World Security](http://sparrow.ece.cmu.edu/~adrian/projects/validation/validation.pdf) - _International Workshop on Cryptographic Techniques and E-Commerce (CrypTEC '99)_


* One can also override the `ServerUserAuthService#resolveWelcomeBanner` method and use whatever other content customization one sees fit.

**Note:**


1. If any of the sources yields an empty string or is missing (in the case of a resource) then no welcome banner message is sent.

2. If the banner is loaded from a file or URL resource, then one can configure the [Charset](https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html) used to convert the file's contents into a string via the `ServerAuthenticationManager.WELCOME_BANNER_CHARSET` configuration key (default=`UTF-8`).

3. In this context, see also the `ServerAuthenticationManager.WELCOME_BANNER_LANGUAGE` configuration key - which provides control over the declared language tag, although most clients seem to ignore it.


### Welcome banner sending phase

According to [RFC 4252 - section 5.4](https://tools.ietf.org/html/rfc4252#section-5.4):

> The SSH server may send an SSH_MSG_USERAUTH_BANNER message at any time after this authentication protocol starts and before authentication is successful.


The code contains a `WelcomeBannerPhase` enumeration that can be used to configure via the `ServerAuthenticationManager.WELCOME_BANNER_PHASE` configuration key the authentication phase at which the welcome banner is sent (see also the `ServerAuthenticationManager.DEFAULT_BANNER_PHASE` value). In this context, note that if the `NEVER` phase is configured, no banner will be sent even if one has been configured via one of the methods mentioned previously.


## `HostConfigEntryResolver`

This interface provides the ability to intervene during the connection and authentication phases and "re-write" the user's original parameters. The `DefaultConfigFileHostEntryResolver` instance used to set up the default client instance follows the [SSH config file](https://www.digitalocean.com/community/tutorials/how-to-configure-custom-connection-options-for-your-ssh-client) standards, but the interface can be replaced so as to implement whatever proprietary logic is required.


```java

    SshClient client = SshClient.setupDefaultClient();
    client.setHostConfigEntryResolver(new MyHostConfigEntryResolver());
    client.start();

    /*
     * The resolver might decide to connect to some host2/port2 using user2 and password2
     * (or maybe using some key instead of the password).
     */
    try (ClientSession session = client.connect(user1, host1, port1).verify(...timeout...).getSession()) {
        session.addPasswordIdentity(...password1...);
        session.auth().verify(...timeout...);
    }
```


## `SshConfigFileReader`

Can be used to read various standard SSH [client](http://linux.die.net/man/5/ssh_config) or [server](http://manpages.ubuntu.com/manpages/precise/en/man5/sshd_config.5.html) configuration files and initialize the client/server respectively. Including (among other things), bind address, ciphers, signature, MAC(s), KEX protocols, compression, welcome banner, etc..

## Event listeners

The code supports registering many types of event listeners that enable receiving notifications about important events as well as sometimes intervening in the way these events are handled. All listener interfaces extend `SshdEventListener` so they can be easily detected and distinguished from other `EventListener`(s).

In general, event listeners are **cumulative** - e.g., any channel event listeners registered on the `SshClient/Server` are automatically added to all sessions, *in addition* to any such listeners registered on the `Session`, as well as any specific listeners registered on a specific `Channel` - e.g.,


```java

    // Any channel event will be signalled to ALL the registered listeners
    sshClient/Server.addChannelListener(new Listener1());
    sshClient/Server.addSessionListener(new SessionListener() {
        @Override
        public void sessionCreated(Session session) {
            session.addChannelListener(new Listener2());
            session.addChannelListener(new ChannelListener() {
                @Override
                public void channelInitialized(Channel channel) {
                    channel.addChannelListener(new Listener3());
                }
            });
        }
    });

```


### `SessionListener`

Informs about session related events. One can modify the session - although the modification effect depends on the session's **state**. E.g., if one changes the ciphers *after* the key exchange (KEX) phase, then they will take effect only if the keys are re-negotiated. It is important to read the documentation very carefully and understand at which stage each listener method is invoked and what are the repercussions of changes at that stage. In this context, it is worth mentioning that one can attach to sessions **arbitrary attributes** that can be retrieved by the user's code later on:


```java

    public static final AttributeKey<String> STR_KEY = new AttributeKey<>();
    public static final AttributeKey<Long> LONG_KEY = new AttributeKey<>();

    sshClient/Server.addSessionListener(new SessionListener() {
        @Override
        public void sessionCreated(Session session) {
            session.setAttribute(STR_KEY, "Some string value");
            session.setAttribute(LONG_KEY, 3777347L);
            // ...etc...
        }

        @Override
        public void sessionClosed(Session session) {
            String str = session.getAttribute(STR_KEY);
            Long l = session.getAttribute(LONG_KEY);
            // ... do something with the retrieved attributes ...
        }
    });
```

### `ChannelListener`


Informs about channel related events - as with sessions, once can influence the channel to some extent, depending on the channel's **state**. The ability to influence channels is much more limited than sessions. In this context, it is worth mentioning that one can attach to channels **arbitrary attributes** that can be retrieved by the user's code later on - same was as it is done for sessions.


### `SignalListener`

Informs about signal requests as described in [RFC 4254 - section 6.9](https://tools.ietf.org/html/rfc4254#section-6.9), break requests (sent as SIGINT) as described in [RFC 4335](https://tools.ietf.org/html/rfc4335) and "window-change" (sent as SIGWINCH) requests as described in [RFC 4254 - section 6.7](https://tools.ietf.org/html/rfc4254#section-6.7)


### `SftpEventListener`

Provides information about major SFTP protocol events. The listener is registered at the `SftpSubsystemFactory`:


```java

    SftpSubsystemFactory factory = new SftpSubsystemFactory();
    factory.addSftpEventListener(new MySftpEventListener());
    sshd.setSubsystemFactories(Collections.<NamedFactory<Command>>singletonList(factory));

```


### `PortForwardingEventListener`

Informs and allows tracking of port forwarding events as described in [RFC 4254 - section 7](https://tools.ietf.org/html/rfc4254#section-7) as well as the (simple) [SOCKS](https://en.wikipedia.org/wiki/SOCKS) protocol (versions 4, 5). In this context, one can create a `PortForwardingTracker` that can be used in a `try-with-resource` block so that the set up forwarding is automatically torn down when the tracker is `close()`-d:


```java

    try (ClientSession session = client.connect(user, host, port).verify(...timeout...).getSession()) {
        session.addPasswordIdentity(password);
        session.auth().verify(...timeout...);

        try (PortForwardingTracker tracker = session.createLocal/RemotePortForwardingTracker(...)) {
            ...do something that requires the tunnel...
        }

        // Tunnel is torn down when code reaches this point
    }
```


### `ScpTransferEventListener`

Inform about SCP related events. `ScpTransferEventListener`(s) can be registered on *both* client and server side:


```java

    // Server side
    ScpCommandFactory factory = new ScpCommandFactory(...with/out delegate..);
    factory.addEventListener(new MyServerSideScpTransferEventListener());
    sshd.setCommandFactory(factory);

    // Client side
    try (ClientSession session = client.connect(user, host, port).verify(...timeout...).getSession()) {
        session.addPasswordIdentity(password);
        session.auth().verify(...timeout...);

        ScpClient scp = session.createScpClient(new MyClientSideScpTransferEventListener());
        ...scp.upload/download...
    }
```


### Reserved messages

The implementation can be used to intercept and process the [SSH_MSG_IGNORE](https://tools.ietf.org/html/rfc4253#section-11.2), [SSH_MSG_DEBUG](https://tools.ietf.org/html/rfc4253#section-11.3) and [SSH_MSG_UNIMPLEMENTED](https://tools.ietf.org/html/rfc4253#section-11.4) messages. The handler can be registered on either side - server
or client, as well as on the session. A special [patch](https://issues.apache.org/jira/browse/SSHD-699) has been introduced
that automatically ignores such messages if they are malformed - i.e., they never reach the handler.

#### SSH message stream "stuffing" and keys re-exchange

[RFC 4253 - section 9](https://tools.ietf.org/html/rfc4253#section-9) recommends re-exchanging keys every once in a while
based on the amount of traffic and the selected cipher - the matter is further clarified in [RFC 4251 - section 9.3.2](https://tools.ietf.org/html/rfc4251#section-9.3.2). These recommendations are mirrored in the code via the `FactoryManager`
related `REKEY_TIME_LIMIT`, `REKEY_PACKETS_LIMIT` and `REKEY_BLOCKS_LIMIT` configuration properties that
can be used to configure said behavior - please be sure to read the relevant _Javadoc_ as well the aforementioned RFC section(s) when
manipulating them. This behavior can also be controlled programmatically by overriding the `AbstractSession#isRekeyRequired()` method.

As an added security mechanism [RFC 4251 - section 9.3.1](https://tools.ietf.org/html/rfc4251#section-9.3.1) recommends adding
"spurious" [SSH_MSG_IGNORE](https://tools.ietf.org/html/rfc4253#section-11.2) messages. This functionality is mirrored in the
`FactoryManager` related `IGNORE_MESSAGE_FREQUENCY`, `IGNORE_MESSAGE_VARIANCE` and `IGNORE_MESSAGE_SIZE`
configuration properties that can be used to configure said behavior - please be sure to read the relevant _Javadoc_ as well the aforementioned RFC section when manipulating them. This behavior can also be controlled programmatically by overriding the `AbstractSession#resolveIgnoreBufferDataLength()` method.

#### `ReservedSessionMessagesHandler`

```java

    // client side
    SshClient client = SshClient.setupDefaultClient();
    // This is the default for ALL sessions unless specifically overridden
    client.setReservedSessionMessagesHandler(new MyClientSideReservedSessionMessagesHandler());
    // Adding it via a session listener
    client.setSessionListener(new SessionListener() {
            @Override
            public void sessionCreated(Session session) {
                // Overrides the one set at the client level.
                if (isSomeSessionOfInterest(session)) {
                    session.setReservedSessionMessagesHandler(new MyClientSessionReservedSessionMessagesHandler(session));
                }
            }
    });

    try (ClientSession session = client.connect(user, host, port).verify(...timeout...).getSession()) {
        // setting it explicitly
        session.setReservedSessionMessagesHandler(new MyOtherClientSessionReservedSessionMessagesHandler(session));
        session.addPasswordIdentity(password);
        session.auth().verify(...timeout...);

        ...use the session...
    }


    // server side
    SshServer server = SshServer.setupDefaultServer();
    // This is the default for ALL sessions unless specifically overridden
    server.setReservedSessionMessagesHandler(new MyServerSideReservedSessionMessagesHandler());
    // Adding it via a session listener
    server.setSessionListener(new SessionListener() {
            @Override
            public void sessionCreated(Session session) {
                // Overrides the one set at the server level.
                if (isSomeSessionOfInterest(session)) {
                    session.setReservedSessionMessagesHandler(new MyServerSessionReservedSessionMessagesHandler(session));
                }
            }
    });

```

**NOTE:** Unlike "regular" event listeners, the handler is not cumulative - i.e., setting it overrides the previous instance
rather than being accumulated. However, one can use the `EventListenerUtils` and create a cumulative listener - see how
`SessionListener` or `ChannelListener` proxies were implemented.


### `RequestHandler`(s)

The code supports both [global](https://tools.ietf.org/html/rfc4254#section-4) and [channel-specific](https://tools.ietf.org/html/rfc4254#section-5.4) requests via the registration of `RequestHandler`(s).
The global handlers are derived from `ConnectionServiceRequestHandler`(s) whereas the channel-specific
ones are derived from `ChannelRequestHandler`(s). In order to add a handler one need only register the correct
implementation and handle the request when it is detected. For global request handlers this is done by registering
them on the server:

```java

    // NOTE: the following code can be employed on BOTH client and server - the example is for the server
    SshServer server = SshServer.setUpDefaultServer();
    List<RequestHandler<ConnectionService>> oldGlobals = server.getGlobalRequestHandlers();
    // Create a copy in case current one is null/empty/un-modifiable
    List<RequestHandler<ConnectionService>> newGlobals = new ArrayList<>();
    if (GenericUtils.size(oldGlobals) > 0) {
        newGlobals.addAll(oldGLobals);
    }
    newGlobals.add(new MyGlobalRequestHandler());
    server.setGlobalRequestHandlers(newGlobals);
    
```

For channel-specific requests, one uses the channel's `add/removeRequestHandler` method to manage its handlers. The way request handlers are invoked when a global/channel-specific request is received  is as follows:

* All currently registered handlers' `process` method is invoked with the request type string parameter (among others).
The implementation should examine the request parameters and decide whether it is able to process it.


* If the handler returns `Result.Unsupported` then the next registered handler is invoked.
In other words, processing stops at the **first** handler that returned a valid response. Thus the importance of
the `List<RequestHandler<...>>` that defines the **order** in which the handlers are invoked. **Note**: while
it is possible to register multiple handlers for the same request and rely on their order, it is highly recommended
to avoid this situation as it makes debugging the code and diagnosing problems much more difficult.


* If no handler reported a valid result value then a failure message is sent back to the peer. Otherwise, the returned
result is translated into the appropriate success/failure response (if the sender asked for a response). In this context,
the handler may choose to build and send the response within its own code, in which case it should return the
`Result.Replied` value indicating that it has done so. 


```java

    public class MySpecialChannelRequestHandler implements ChannelRequestHandler {
        ...
        
        @Override
        public Result process(Channel channel, String request, boolean wantReply, Buffer buffer) throws Exception {
            if (!"my-special-request".equals(request)) {
               return Result.Unsupported;   // Not mine - maybe someone else can handle it
            }
            
            ...handle the request - can read more parameters from the message buffer...
            
            return Result.ReplySuccess/Failure/Replied; // signal processing result
        }
    }
```


#### Default registered handlers

* `exit-signal`, `exit-status` - As described in [RFC4254 section 6.10](https://tools.ietf.org/html/rfc4254#section-6.10)


* `*@putty.projects.tartarus.org` - As described in [Appendix F: SSH-2 names specified for PuTTY](http://tartarus.org/~simon/putty-snapshots/htmldoc/AppendixF.html)


* `hostkeys-prove-00@openssh.com`, `hostkeys-00@openssh.com` - As described in [OpenSSH protocol - section 2.5](https://github.com/openssh/openssh-portable/blob/master/PROTOCOL)


* `tcpip-forward`, `cancel-tcpip-forward` - As described in [RFC4254 section 7](https://tools.ietf.org/html/rfc4254#section-7)


* `keepalive@*` - Used by many implementations (including this one) to "ping" the peer and make sure the connection is still alive.
In this context, the SSHD code allows the user to configure both the frequency and content of the heartbeat request (including whether to send this request at all) via the `ClientFactoryManager`-s `HEARTBEAT_INTERVAL`, `HEARTBEAT_REQUEST` and `DEFAULT_KEEP_ALIVE_HEARTBEAT_STRING` configuration properties.


* `no-more-sessions@*` - As described in [OpenSSH protocol section 2.2](https://github.com/openssh/openssh-portable/blob/master/PROTOCOL). In this context, the code consults the `ServerFactoryManagder.MAX_CONCURRENT_SESSIONS` server-side configuration property in order to
decide whether to accept a successfully authenticated session. 


# Extension modules

There are several extension modules available

## Command line clients

The _apache-sshd.zip_ distribution provides `Windows/Linux` scripts that use the MINA SSHD code base to implement the common _ssh, scp, sftp_ commands. The clients accept most useful switches from the original commands they mimic, where the `-o Option=Value` arguments can be used to configure the client/server in addition to the system properties mechanism. For more details, consult the _main_ methods code in the respective `SshClient`, `SftpCommand` and `DefaultScpClient` classes. The code also includes `SshKeyScan#main` that is a simple implementation for [ssh-keyscan(1)](https://www.freebsd.org/cgi/man.cgi?query=ssh-keyscan&sektion=1).

The distribution also includes also an _sshd_ script that can be used to launch a server instance - see `SshServer#main` for activation command line arguments and options.

## GIT support

The _sshd-git_ artifact contains server-side command factories for handling some _git_ commands - see `GitPackCommandFactory` and `GitPgmCommandFactory`. These command factories accept a delegate to which non-_git_ commands are routed:


```java

    sshd.setCommandFactory(new GitPackCommandFactory(rootDir, new MyCommandFactory()));

    // Here is how it looks if SCP is also requested
    sshd.setCommandFactory(new GitPackCommandFactory(rootDir, new ScpCommandFactory(new MyCommandFactory())))
    // or
    sshd.setCommandFactory(new ScpCommandFactory(new GitPackCommandFactory(rootDir, new MyCommandFactory())))
    // or
    sshd.setCommandFactory(new GitPackCommandFactory(rootDir, new ScpCommandFactory(new MyCommandFactory())))
    // or any other combination ...
```


## LDAP adaptors

The _sshd-ldap_ artifact contains an [LdapPasswordAuthenticator](https://issues.apache.org/jira/browse/SSHD-607) and an [LdapPublicKeyAuthenticator](https://issues.apache.org/jira/browse/SSHD-608) that have been written along the same lines as the [openssh-ldap-publickey](https://github.com/AndriiGrytsenko/openssh-ldap-publickey) project. The authenticators can be easily configured to match most LDAP schemes, or alternatively serve as base classes for code that extends them and adds proprietary logic.

## PROXY / SSLH protocol hooks

The code contains [support for "wrapper" protocols](https://issues.apache.org/jira/browse/SSHD-656) such as [PROXY](http://www.haproxy.org/download/1.6/doc/proxy-protocol.txt) or  [sslh](http://www.rutschle.net/tech/sslh.shtml). The idea is that one can register either a `ClientProxyConnector` or `ServerProxyAcceptor` and intercept the 1st packet being sent/received (respectively) **before** it reaches the SSHD code. This gives the programmer the capability to write a front-end that routes outgoing/incoming packets:

* `SshClient/ClientSesssion#setClientProxyConnector` - sets a proxy that intercepts the 1st packet before being sent to the server

* `SshServer/ServerSession#setServerProxyAcceptor` - sets a proxy that intercept the 1st incoming packet before being processed by the server

# Builtin components

Below is the list of builtin components:

* **Ciphers**: aes128cbc, aes128ctr, aes192cbc, aes192ctr, aes256cbc, aes256ctr, arcfour128, arcfour256, blowfishcbc, tripledescbc
* **Digests**: md5, sha1, sha224, sha384, sha512
* **Macs**: hmacmd5, hmacmd596, hmacsha1, hmacsha196, hmacsha256, hmacsha512
* **Key exchange**: dhg1, dhg14, dhgex, dhgex256, ecdhp256, ecdhp384, ecdhp521
* **Compressions**: none, zlib, zlib@openssh.com
* **Signatures**: ssh-dss, ssh-rsa, nistp256, nistp384, nistp521

