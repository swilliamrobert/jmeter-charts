base=$1

find ./ -name "*-responses.csv" | while read file
do
 folder=$(dirname "$file")
 createChart.sh $folder $(basename $folder)
done