import os
import csv

def loop(inputFile='input.txt', changeMe='$', metric='DWD', metric2 ='DiffractionEfficiency'):

    # set of values to change
    vals = [1, 2, 5, 10, 20, 50, 100]
   # vals = [8, 12, 15, 18, 25, 30, 40]
    beam_rat = [0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5 ,2.0, 3.0, 4.0, 5.0]
  #  beam_rat = [1]
    overall = [["Crystal Size", "ppm", "Beam size", "Beam ratio", "DWD", "DE"]]

    extractedMetrics = []
    beam_size = 0

    for v in vals: #every crystal size
       # ppm = 10/float(v)
        ppm = 20 * (1/float(v))
        for ratio in beam_rat:
            lines = []
            beam_size = float(v) * ratio
            # change input file for current value
            newInputFile = inputFile.replace('.txt', '-updated.txt')
            f = open(inputFile, 'r')
            g = open(newInputFile, 'w')
            for l in f.readlines():
                l = l.replace('$', str(v))
                l = l.replace('?', str(ppm))
                l = l.replace('@', str(beam_size))
                lines.append(l)
            for value in lines:
                g.write(value)
            f.close()
            g.close()
            '''
            newerInputFile = newInputFile.replace('.txt', '-updated.txt')
            f = open(newInputFile, 'r')
            g = open(newerInputFile, 'w')
            for l in f.readlines():
                g.write(l.replace(alsoChangeMe, str(ppm)))
            f.close()
            g.close()
            '''


            # os.system('java -jar RD3DDone.jar -i ' + newerInputFile)
            os.system('java -jar raddose3d.jar -i ' + newInputFile)

            # parse the output file
            f = open('./output-Summary.csv', 'r')
            met = 0.0
            met2 = 0.0
            for i, l in enumerate(f.readlines()):
                l = l.replace(' ', '')
                if i == 0:
                    # get metric place in csv file
                    ind = l.split(',').index(metric)
                    ind2 = l.split(',').index(metric2)
                else:
                    # extract the metric value
                    met = float(l.split(',')[ind])
                    met2 = float(l.split(',')[ind2])
                    extractedMetrics.append(met)
                    break
            f.close()


            overall.append([v, ppm, beam_size, ratio, met, met2])

    '''
    # write new csv file
    f = open('./loop-metrics.csv', 'w')
    f.write(','.join(map(str, extractedMetrics)))
    f.close()
    '''

    f = open('./beam-ratio.csv', 'wb')
    writer = csv.writer(f)
    writer.writerows(overall)
    f.close()

if __name__ == "__main__":
    loop()
