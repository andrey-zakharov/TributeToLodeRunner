
mkdir -p tiles
cd tiles
convert ../tiles.png -crop '23x25' +repage temptiles.%02d.png
for f in temptiles.*.png; do if [ `identify -format '%w' "$f"` -lt 23 ]; then rm $f; fi; done
convert temptiles.*.png -crop 20x22+0+0 +repage tiles.png
convert ../hole.png -crop '20x22' +repage hole.%02d.png
rm hole.{05,06,07,08,16,23,24,25,34,35}.png #empty tiles
mv hole.17.png hole.50.png
mv hole.26.png hole.51.png

montage tiles*.png hole*.png -background transparent -geometry +0 -quality 100 ../tiles.out.png