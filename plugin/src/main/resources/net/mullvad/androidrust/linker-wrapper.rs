use std::env;
use std::fs;
use std::process::Command;

fn update_args(args: &mut Vec<String>, ndk_major_version: i32) {
    if ndk_major_version >= 23 {
        for arg in args.iter_mut() {
            if arg.starts_with("-lgcc") {
                *arg = format!("-lunwind{}", &arg[5..]);
            }
        }
    }
}

fn main() {
    let ndk_major_version = env::var("CARGO_NDK_MAJOR_VERSION")
        .unwrap_or_default()
        .parse::<i32>()
        .unwrap_or(0);

    let cc = env::var("RUST_ANDROID_GRADLE_CC").expect("RUST_ANDROID_GRADLE_CC not set");
    let cc_link_arg = env::var("RUST_ANDROID_GRADLE_CC_LINK_ARG").expect("RUST_ANDROID_GRADLE_CC_LINK_ARG not set");

    let mut args: Vec<String> = env::args().skip(1).collect();

    // Update main arguments
    update_args(&mut args, ndk_major_version);

    // Update response files if any
    for arg in &args {
        if arg.starts_with("@") {
            let path = &arg[1..];
            if let Ok(content) = fs::read_to_string(path) {
                // Split into lines while preserving line endings as much as possible
                // The python code used splitlines(keepends=True)
                // We'll simulate this by looking for line boundaries.
                let mut lines = Vec::new();
                let mut start = 0;
                for (i, c) in content.char_indices() {
                    if c == '\n' {
                        lines.push(content[start..=i].to_string());
                        start = i + 1;
                    }
                }
                if start < content.len() {
                    lines.push(content[start..].to_string());
                }

                let mut modified = false;
                for line in lines.iter_mut() {
                    if ndk_major_version >= 23 && line.starts_with("-lgcc") {
                        *line = format!("-lunwind{}", &line[5..]);
                        modified = true;
                    }
                }

                if modified {
                    let new_content = lines.concat();
                    let _ = fs::write(path, new_content);
                }
            }
        }
    }

    let status = Command::new(&cc)
        .arg(cc_link_arg)
        .args(&args)
        .status()
        .expect("Failed to execute linker");

    std::process::exit(status.code().unwrap_or(1));
}
