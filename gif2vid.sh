ffmpeg -hide_banner -i "$1" -c:v libx264 -preset veryslow -crf 17 -vf "fps=60,scale=640:-1" -loop 0 output.mkv
