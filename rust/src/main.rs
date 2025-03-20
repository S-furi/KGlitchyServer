use core::fmt;
use std::{
    collections::HashMap, env, io::{self, BufRead, BufReader, ErrorKind, Read, Write}, net::{TcpStream, ToSocketAddrs}, str::SplitTerminator, thread::sleep, time::Duration, usize
};

use sha2::{digest::core_api::ExtendableOutputCore, Digest, Sha256};

const SERVER_URL: &str = "http://localhost:8080";

#[derive(Debug)]
pub enum HttpError {
    ConnectionError(String),
    TimeoutError,
    InvalidURL(String),
    InvalidResponse(String),
    IOError(io::Error),
}

impl From<io::Error> for HttpError {
    fn from(value: io::Error) -> Self {
        match value.kind() {
            io::ErrorKind::TimedOut => HttpError::TimeoutError,
            io::ErrorKind::ConnectionRefused => {
                HttpError::ConnectionError(format!("Connection refused: {}", value))
            }
            _ => HttpError::IOError(value),
        }
    }
}

impl fmt::Display for HttpError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            HttpError::IOError(e) => write!(f, "I/O error: {}", e),
            HttpError::ConnectionError(msg) => write!(f, "Connection error: {}", msg),
            HttpError::TimeoutError => write!(f, "Request timed out!"),
            HttpError::InvalidURL(msg) => write!(f, "Invalid HTTP url: {}", msg),
            HttpError::InvalidResponse(msg) => write!(f, "Invalid HTTP response: {}", msg),
        }
    }
}

#[derive(Debug)]
pub struct Response {
    data: Vec<u8>,
    expected_length: usize,
    status_code: u16,
    status_text: String,
    headers: HashMap<String, String>,
}

fn parse_response(stream: TcpStream) -> Result<Response, HttpError> {
    let mut reader = BufReader::new(stream);

    let mut status_line = String::new();
    let bytes_read = reader
        .read_line(&mut status_line)
        .map_err(|e| match e.kind() {
            io::ErrorKind::TimedOut => HttpError::TimeoutError,
            _ => HttpError::IOError(e),
        })?;

    if bytes_read == 0 {
        return Err(HttpError::InvalidResponse(
            "Received an empty response.".to_string(),
        ));
    }

    let status_parts: Vec<&str> = status_line.split_whitespace().collect();

    if status_parts.len() < 3 {
        return Err(HttpError::InvalidResponse(format!(
            "Invalid status line: {}",
            status_line.trim()
        )));
    }

    if !status_parts[0].starts_with("HTTP/") {
        return Err(HttpError::InvalidResponse(format!(
            "Invalid HTTP version: '{}'",
            status_parts[0].trim()
        )));
    }

    let status_code = status_parts[1].parse::<u16>().map_err(|_| {
        HttpError::InvalidResponse(format!("Invalid status code: {}", status_parts[1]))
    })?;

    let status_text = status_parts[2..]
        .join(" ")
        .trim_end_matches("\r\n")
        .to_string();

    let mut headers = HashMap::new();

    loop {
        let mut line = String::new();
        let bytes_read = reader.read_line(&mut line).map_err(|e| match e.kind() {
            io::ErrorKind::TimedOut => HttpError::TimeoutError,
            _ => HttpError::IOError(e),
        })?;
        if bytes_read == 0 {
            return Err(HttpError::InvalidResponse(
                "Unexpected end of headers!".to_string(),
            ));
        }

        if line.trim().is_empty() {
            break;
        }

        if let Some(idx) = line.find(':') {
            let key = line[..idx].trim().to_string();
            let value = line[idx + 1..].trim_end_matches("\r\n").trim().to_string();
            headers.insert(key, value);
        } else {
            return Err(HttpError::InvalidResponse(format!(
                "Invalid header format: {}",
                line.trim()
            )));
        }
    }

    let expected_length: usize = match headers.get("Content-Length") {
        Some(length) => length
            .parse::<usize>()
            .map_err(|_| HttpError::InvalidResponse(format!("Invalid length: {}", length)))?,
        _ => 0,
    };

    let mut data = Vec::new();
    reader.read_to_end(&mut data).map_err(|e| match e.kind() {
        io::ErrorKind::TimedOut => HttpError::TimeoutError,
        _ => HttpError::IOError(e),
    })?;

    Ok(Response {
        data,
        expected_length,
        status_code,
        status_text,
        headers,
    })
}

fn get_data(range: Option<(usize, usize)>) -> Result<Response, HttpError> {
    let timeout = Duration::from_secs(30);
    let stream = match TcpStream::connect_timeout(
        &SERVER_URL
            .to_socket_addrs()
            .map_err(|e| HttpError::ConnectionError(format!("Failed to resolve host: {}", e)))?
            .next()
            .ok_or_else(|| HttpError::ConnectionError("No address found for host".to_string()))?,
        timeout,
    ) {
        Ok(s) => s,
        Err(e) if e.kind() == io::ErrorKind::TimedOut => return Err(HttpError::TimeoutError),
        Err(e) => return Err(HttpError::from(e)),
    };

    stream
        .set_read_timeout(Some(timeout))
        .map_err(|e| HttpError::from(e))?;

    stream
        .set_write_timeout(Some(timeout))
        .map_err(|e| HttpError::from(e))?;

    let mut request = format!("GET / HTTP/1.1\r\n");
    request.push_str(&format!("Host: localhost:8080\r\n"));
    request.push_str("Connection: close\r\n");

    if let Some((lb, up)) = range {
        request.push_str(&format!("Range: bytes={}-{}\r\n", lb, up));
    }

    request.push_str("\r\n");

    let mut write_stream = stream.try_clone().map_err(|e| HttpError::from(e))?;

    write_stream
        .write_all(request.as_bytes())
        .map_err(|e| match e.kind() {
            io::ErrorKind::BrokenPipe => {
                HttpError::ConnectionError("Connection closed by server".to_string())
            }
            io::ErrorKind::TimedOut => HttpError::TimeoutError,
            _ => HttpError::from(e),
        })?;

    parse_response(stream)
}

fn get_missing_chunks(initial_idx: usize, final_length: usize, chunk_size: usize) -> Vec<u8> {
    let mut current_idx = initial_idx;
    std::iter::from_fn(move || {
        if current_idx >= final_length {
            return None;
        }

        let end_idx = std::cmp::min(current_idx + chunk_size, final_length);

        println!("Getting range from {} to {}", current_idx, end_idx);

        let result = get_data(Some((current_idx, end_idx)));

        if result.is_err() {
            return None;
        }

        std::thread::sleep(std::time::Duration::from_millis(500));
        current_idx += chunk_size;
        Some(result.unwrap().data)
    }).flatten().collect()
}


fn main() {
    let args: Vec<String> = env::args().collect();
    let expected_hash = args.get(1).map(|s| s.to_lowercase());

    let data = get_data(None).expect("Cannot perform first request...");
    let mut res = data.data;
    let mut missing_data = get_missing_chunks(res.len(), data.expected_length, 64 * 1024);
    res.append(&mut missing_data);

    let mut hasher = Sha256::new();
    hasher.update(&res);
    let result = &hasher.finalize()[..];
    let computed_hash = result.iter()
        .map(|b| format!("{:02x}", b))
        .collect::<String>();

    println!("Res len: {} --> expeted: {}", &res.len(), data.expected_length);
    println!("Hashed value: {}", computed_hash);

    if let Some(original_hash) = expected_hash {
        if computed_hash == original_hash {
            println!("Hashes match!")
        } else {
            println!("Hashes do NOT match!")
        }
    }
}
