# KGlitchyServer 

## Task Description

Here is a [glitchy HTTP server written in Python](https://gist.github.com/vladimirlagunov/dcdf90bb19e9de306344d46f20920dce). The server sends some randomized data on every GET request, but often it doesn’t send the whole data, but just a part of the data. Luckily, the server supports the HTTP header “Range”.

Run the server in a terminal.

You need to write a client application that downloads the binary data from the glitchy server. To ensure that the downloaded data is correct, check the SHA-256 hash of the downloaded data. The hash must be the same as the HTTP server writes into the terminal.

Write the client app either in Kotlin+JVM or in Rust.

If possible, avoid using external libraries.

## Usage

### Kotlin Script (Beta)

Run:
```bash
./solution.kts
```
And manually compare hashed value, or:

```bash
./solution.kts <sha256-digest>
```

To let the script check automatically original hash and computed hash.

### Kotlin Project

Move to [`ktjvm/KGlitchyServer/`](https://github.com/S-furi/KGlitchyServer/tree/main/ktjvm/KGlitchyServer), and run

```bash
./gradlew run
```

And manually compare hashed value, or:

```bash
./gradlew --args <sha256-digest>
```

To let the program check automatically original hash and computed hash.
