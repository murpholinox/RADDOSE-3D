package se.raddo.raddose3D;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


public class XFEL {
  //polyhderon variables
  public double[][] verticesXFEL;
  public int[][] indicesXFEL;
  public double[][][][] crystCoordXFEL;
  public double crystalPixPerUMXFEL;
  public int[] crystalSizeVoxelsXFEL;
  public boolean[][][][] crystOccXFEL;
  /**
   * Normal array holding normalised direction vectors for
   * each triangle specified by the index array.
   * Contains an i, j, k vector per triangle.
   * Should have same no. of entries as the indices array.
   */
  private double[][]            normals, rotatedNormals;
  
  /**
   * Distances from origin for each of the triangle planes.
   * Should have same no. of entries as the indices array.
   */
  private double[]              originDistances, rotatedOriginDistances;
  
  public double XDimension; //nm
  public double YDimension;
  public double ZDimension;
  
  public double[] dose;
  public double[] photonDose;
  public double[] electronDose;
  public double raddoseStyleDose;
  
  private double lastTime;
  
  private double[] angularEmissionProbs;
  private final int numberAngularEmissionBins = 10;
  
  private TreeMap<Double, double[]>[]  lowEnergyAngles;
  private TreeMap<Double, double[]>[]  highEnergyAngles;
  
  protected static final long NUM_PHOTONS = 50000000;
  protected static final long PULSE_LENGTH = 70; //length in fs
  protected static final double PULSE_BIN_LENGTH = 0.5; //length in fs
  protected static final double PULSE_ENERGY = 2.11E-3; //energy in J
  protected static final double c = 299792458; //m/s
  protected static final double m = 9.10938356E-31; // in Kg
  
  public XFEL(double vertices[][], int[][] indices, double[][][][] crystCoord, 
      double crystalPixPerUM, int[] crystSizeVoxels, boolean[][][][] crystOcc) {
    verticesXFEL = vertices;
    indicesXFEL = indices;
    crystCoordXFEL = crystCoord;
    crystalPixPerUMXFEL = crystalPixPerUM;
    crystalSizeVoxelsXFEL = crystSizeVoxels;
    crystOccXFEL = crystOcc;
    
    double[] xMinMax = this.minMaxVertices(0, vertices);
    double[] yMinMax = this.minMaxVertices(1, vertices);
    double[] zMinMax = this.minMaxVertices(2, vertices);
    XDimension = 1000 * (xMinMax[1] - xMinMax[0]);
    YDimension = 1000 * (yMinMax[1] - yMinMax[0]);
    ZDimension = 1000 * (zMinMax[1] - zMinMax[0]);
    
    //these break way way too easily so need a more permanent solution
    dose = new double[(int) (PULSE_LENGTH/PULSE_BIN_LENGTH + (50/PULSE_BIN_LENGTH))];
    photonDose = new double[(int) (PULSE_LENGTH/PULSE_BIN_LENGTH + (50/PULSE_BIN_LENGTH))];
    electronDose = new double[(int) (PULSE_LENGTH/PULSE_BIN_LENGTH + (50/PULSE_BIN_LENGTH))];
    
    lowEnergyAngles = new TreeMap[95];
    highEnergyAngles = new TreeMap[95];
  }
  
  public void CalculateXFEL(Beam beam, Wedge wedge, CoefCalc coefCalc) {
    startMonteCarloXFEL(beam, wedge, coefCalc);
    processDose(beam, coefCalc);
    System.out.println("XFEL done");
    
  }
  
  public void startMonteCarloXFEL(Beam beam, Wedge wedge, CoefCalc coefCalc) {
    //get absorption coefficient
    coefCalc.updateCoefficients(beam);
    double absCoef = coefCalc.getAbsorptionCoefficient(); //um-1
    double photonMFPL = (1/absCoef)*1000; //just photoelectric absorption for now can put in Compton later
    
    //populate the relative element cross sections here 
    Map<Element, Double> elementAbsorptionProbs = coefCalc.getPhotoElectricProbsElement(beam.getPhotonEnergy());
    //populate the relative shell cross sections
    Map<Element, double[]> ionisationProbs = getRelativeShellProbs(elementAbsorptionProbs, beam.getPhotonEnergy());
    //populate the angular emission probs
    populateAngularEmissionProbs();
    
    //elastic elect5ron angle setup
    coefCalc.populateCrossSectionCoefficients();
    
    //Decide a starting time stamp for the photons
    double photonDivisions =  NUM_PHOTONS / (PULSE_LENGTH/PULSE_BIN_LENGTH);

    double xn = 0, yn = 0, zn = 0;
    for (int i = 0; i < NUM_PHOTONS; i++) { //for every electron to simulate
      double timeStamp = ((int) (i/ photonDivisions)) * PULSE_BIN_LENGTH;
      //firstly I need to get a position of the beam on the sample. the direction will simply be 0 0 1
      double xNorm = 0.0000, yNorm = 0.0000, zNorm = 1.0; //direction cosine are such that just going down in one
      double theta = 0, phi = 0, previousTheta = 0, previousPhi = 0, thisTheta = 0;
      double previousZ = -ZDimension/2;  //dodgy if specimen not flat - change for concave holes    
      //position
      double[] xyPos = getPhotonBeamXYPos(beam);
      double previousX = xyPos[0];
      double previousY = xyPos[1];
      
      //determine if the electron is incident on the sample or not
      boolean surrounding = !isMicrocrystalAt(previousX, previousY, 0); //Z = 0 as just looking at x and y
      //the next step is to work out the distance s
      double s = -photonMFPL*Math.log(Math.random());
      xn = previousX + s * xNorm;
      yn = previousY + s * yNorm;
      zn = previousZ + s * zNorm;
      //to test
   //   zn = 0;
      //now start the simulation
      boolean exited = false;
      while (exited == false) {
        if (isMicrocrystalAt(xn, yn, zn) == true) { //ignoring entry from the surrounding for now
          // if the microcrystal is here a photoelectron will need to be produced
          //determine the time at which this happened = startingTimeStamp + time to this point
          double timeToPoint = (1/c) * (s/1E9); //in seconds
          timeStamp += timeToPoint * 1E15; //time from start of pulse that this happened
          int doseTime = (int) (timeStamp/PULSE_BIN_LENGTH); //rounding down so 0 = 0-0.99999, 1 - 1-1.99999 etc 
          
          //work out the element that has been absorbed with and hence the shell binding energy and photoelectron energy
          
          //element
          double elementRND = Math.random();
          Element ionisedElement = null;
          for (Element e : elementAbsorptionProbs.keySet()) {
            double elementProb =  elementAbsorptionProbs.get(e);
            if (elementProb > elementRND) {
              ionisedElement = e;
              break;
            }
          }
          //shell
          double[] shellProbs = ionisationProbs.get(ionisedElement);
          double shellRND = Math.random();
          int shellIndex = 0;
          for (int j = 0; j < shellProbs.length; j++) {
            if (shellProbs[j] > shellRND) {
              shellIndex = j;
              break;
            }
          }
          //get the shell binding energy
          double shellBindingEnergy = getShellBindingEnergy(ionisedElement, shellIndex);
          double photoelectronEnergy = beam.getPhotonEnergy() - shellBindingEnergy;
          
          //Add the dose (shell binding energy) to the appropriate time
          dose[doseTime] += shellBindingEnergy;
          photonDose[doseTime] += shellBindingEnergy;
          raddoseStyleDose += beam.getPhotonEnergy();
          
          //send out the photoelectron in with the same timestamp of the photon - I think I should have this timestamp as a double
          trackPhotoelectron(coefCalc, timeStamp, photoelectronEnergy, ionisedElement, shellIndex, xn, yn, zn);
          
          
          
          //photon is absorbed so don't need to keep track of it after this and update stuff
          exited = true; // because the photon is absorbed
        }
        else {
          exited = true;
        }
      }
    }
    //get time at which last photon exits the sample
    lastTime = ((1/c) * (ZDimension/1E9) * 1E15) + PULSE_LENGTH;
  }
  
  private void processDose(Beam beam, CoefCalc coefCalc) {
    //just take the whole sample, assuming it is bathed totally
    //and also just take a whole cube for now
    double sampleVolume = XDimension * YDimension * ZDimension * 1E-21; //cm^3
    double sampleMass = ((coefCalc.getDensity() * sampleVolume) / 1000);  //in Kg 
    
    double energyPerPhoton = beam.getPhotonEnergy()*Beam.KEVTOJOULES;
    double numberOfPhotons = PULSE_ENERGY/energyPerPhoton;
    double sumDose = 0, sumElectronDose = 0, sumPhotonDose = 0;
    double sumDoseNoCutOff = 0, sumElectronDoseNoCutOff = 0, sumPhotonDoseNoCutOff = 0;
    for (int i = 0; i < dose.length; i++) {
      dose[i] = ((dose[i] * (numberOfPhotons/NUM_PHOTONS) * Beam.KEVTOJOULES) / sampleMass) /1E6; //in MGy
      electronDose[i] = ((electronDose[i] * (numberOfPhotons/NUM_PHOTONS) * Beam.KEVTOJOULES) / sampleMass) /1E6; //in MGy
      photonDose[i] = ((photonDose[i] * (numberOfPhotons/NUM_PHOTONS) * Beam.KEVTOJOULES) / sampleMass) /1E6; //in MGy
      //sums
      if (i*PULSE_BIN_LENGTH < lastTime-(1*PULSE_BIN_LENGTH)) {
        sumDose += dose[i];
        sumElectronDose += electronDose[i];
        sumPhotonDose += photonDose[i];
      }
      sumDoseNoCutOff += dose[i];
      sumElectronDoseNoCutOff += electronDose[i];
      sumPhotonDoseNoCutOff += photonDose[i];
    }
    raddoseStyleDose = ((raddoseStyleDose * (numberOfPhotons/NUM_PHOTONS) * Beam.KEVTOJOULES) / sampleMass) /1E6; //in MGy
    System.out.println("Photon Dose: " + sumPhotonDose);
    System.out.println("Electron Dose: " + sumElectronDose);
    System.out.println("Dose: " + sumDose);
  }
  
  private double getTimeToDistance(double electronEnergy, double s) {
    double csquared = Math.pow(c, 2);
    double Vo = electronEnergy * Beam.KEVTOJOULES;
    double betaSquared = 1- Math.pow(m*csquared/(Vo + m*csquared), 2);
    double v = Math.pow(betaSquared*csquared, 0.5) * 1E9 / 1E15; //nm/fs
    double timeTos = (1/v) * s;
    return timeTos;
  }
  
  private void trackPhotoelectron(CoefCalc coefCalc, double startingTimeStamp, double startingEnergy, Element ionisedElement, int shellIndex,
                                  double previousX, double previousY, double previousZ) {
    //Choose an initial starting direction based on beam polarisation direction - currently all horizontal and I'm going to assume 100%
    //If shell is not K then send it out randomly

    double theta = 0, phi = 0, xNorm = 0, yNorm = 0, zNorm = 0;
    if (shellIndex == 0) { //then I want to send out in a biased direction
      xNorm = getCosAngleToX();
      //get yNorm and zNorm
      yNorm = PosOrNeg() * Math.random() * Math.pow(1-Math.pow(xNorm, 2), 0.5);
      zNorm = PosOrNeg() * Math.pow(1 - Math.pow(xNorm, 2) - Math.pow(yNorm, 2), 0.5);
      //get theta and phi
      theta = Math.acos(zNorm);
      phi = Math.acos(xNorm / Math.sin(theta));
    }
    else { // send it out in a random direction
      theta = Math.random() * 2 * Math.PI;
      phi = Math.random() * 2 * Math.PI;
      xNorm = Math.sin(theta) * Math.cos(phi);
      yNorm = Math.sin(theta) * Math.sin(phi);
      zNorm = Math.cos(theta);
    }
    //do full Monte Carlo simulation the same way as in MicroED, but with a time stamp and adding dose every step
    //just do stopping power for now dw about surrounding and aUger and fluorescence and stuff
    
    boolean surrounding = false;
    double energyLost = 0;
    double electronEnergy = startingEnergy;
    double timeStamp = startingTimeStamp;
    double startingStoppingPower = coefCalc.getStoppingPower(startingEnergy, surrounding);
    double stoppingPower = startingStoppingPower;
    
    double startingLambda_el = coefCalc.getElectronElasticMFPL(startingEnergy, surrounding);
    Map<ElementEM, Double> elasticProbs = coefCalc.getElasticProbs(surrounding);
    
    double lambdaT = 0;
    lambdaT = startingLambda_el;

    double testRND = Math.random();
    double s = -lambdaT*Math.log(testRND);
 //   double Pinel = 1 - (lambdaT / startingLambda_el);
    double xn = previousX + s * xNorm;
    double yn = previousY + s * yNorm;
    double zn = previousZ + s * zNorm;
    
    boolean exited = false;
    double previousTheta = 0, previousPhi = 0;
    
    if (startingEnergy < 0.05) {
      exited = true;
    }
    while (exited == false) {
      if (isMicrocrystalAt(xn, yn, zn) == true) { //photoelectron still in the crystal
        energyLost = s * stoppingPower;
        //work out how long it took to travel this far 
        double timeToDistance = getTimeToDistance(electronEnergy, s);
        int doseTime = (int) ((timeStamp + (timeToDistance/2))/PULSE_BIN_LENGTH);
        timeStamp += timeToDistance;
        dose[doseTime] += energyLost;  //still just adding keV
        electronDose[doseTime] += energyLost;
       
        //update position and angle
        //update position and angle
        previousTheta = theta;
        previousPhi = phi;
        previousX = xn;
        previousY = yn;
        previousZ = zn;
        
        //update angle and stuff - for now it is always an elastic interaction
        double elasticElementRND = Math.random();
        ElementEM elasticElement = null;
        for (ElementEM e : elasticProbs.keySet()) {
          if (elasticProbs.get(e) > elasticElementRND) { //Then this element is the one that was ionised
            elasticElement = e;
            break;
          }
        }
        
        //get the angles
        //ELSEPA stuff

        theta = getPrimaryElasticScatteringAngle(electronEnergy, elasticElement.getAtomicNumber());

        
        theta = previousTheta + theta;
        if (theta >= (2 * Math.PI)) {
          theta -= 2*Math.PI;
        }
        phi = 2 * Math.PI * Math.random();
        phi = previousPhi + phi;
        if (phi >= (2 * Math.PI)) {
          phi -= 2*Math.PI;
        }
      //now further update the primary
        
        xNorm = Math.sin(theta) * Math.cos(phi);
        yNorm = Math.sin(theta) * Math.sin(phi);
        zNorm = Math.cos(theta);
        
        //update the energy and stopping Power and stuff
        electronEnergy -= energyLost; 
        stoppingPower = coefCalc.getStoppingPower(electronEnergy, false);
        //get new lambdaT
        double lambdaEl = coefCalc.getElectronElasticMFPL(electronEnergy, false);
        lambdaT = lambdaEl;
        s = -lambdaT*Math.log(Math.random());
        elasticProbs = coefCalc.getElasticProbs(false);
        
        //update to new position
        xn = previousX + s * xNorm;
        yn = previousY + s * yNorm;
        zn = previousZ + s * zNorm;
      }
      else { //it's left the crystal
        exited = true;
        //get the energy deposited before it left the crystal. - when I slice need to also do timestamps 
        double escapeDist = 1000 * getIntersectionDistance(previousX, previousY, previousZ, xNorm, yNorm, zNorm); //nm
        double FSEStoppingPower = coefCalc.getStoppingPower(electronEnergy, false);
        double energyToEdge = FSEStoppingPower * escapeDist;
        if (energyToEdge < electronEnergy){ //the FSE has escaped
          double energyLostStep = 0, totFSEenLostLastStep = 0;
          double newEnergy = electronEnergy;
          for (int j = 0; j < 10; j++) { //I will need to play around with the amount of slicing when I am writing up
            energyLostStep = (escapeDist/10) * FSEStoppingPower;
            //add dose to timeStamp
            double timeToDistance = getTimeToDistance(newEnergy, escapeDist/10);
            int doseTime = (int) ((timeStamp + (timeToDistance/2))/PULSE_BIN_LENGTH); // over 2 as adding it half way
            timeStamp += timeToDistance;
            dose[doseTime] += energyLostStep;  //still just adding keV
            electronDose[doseTime] += energyLostStep;
            
            newEnergy -= energyLostStep;
            FSEStoppingPower = coefCalc.getStoppingPower(newEnergy, false);
            if (newEnergy < 0) {
              break;
            }
          } 
          
        }
        else {
          //didn't quite escape, add the electron energy to the dose
          double timeToDistance = getTimeToDistance(electronEnergy, s);
          int doseTime = (int) ((timeStamp + (timeToDistance/2))/PULSE_BIN_LENGTH);
          timeStamp += timeToDistance;
          dose[doseTime] += electronEnergy;  //still just adding keV
          electronDose[doseTime] += electronEnergy;
        }
      }
      if (electronEnergy < 0.05) {
        exited = true;
      }
    }
    
  }
  
  private double getCosAngleToX() {
    double RNDangle = Math.random();
    double lastProb = 0;
    double angle = 0;
    for (int i = 0; i < numberAngularEmissionBins; i++) {
      if (RNDangle < angularEmissionProbs[i]) { //then it's in this angle range
        //interpolate angle
        double angleStart = i * Math.PI/numberAngularEmissionBins;
        double angleEnd = (i+1) * (Math.PI/numberAngularEmissionBins);
        double proportionAlong = (RNDangle - lastProb) / (angularEmissionProbs[i] - lastProb);
        angle = angleStart + (proportionAlong * (angleEnd - angleStart));
        break;
      }
      lastProb = angularEmissionProbs[i];
    }
    return Math.cos(angle);
  }
  private double PosOrNeg() {
    double RND = Math.random();
    if (RND < 0.5) {
      return 1;
    }
    else {
      return -1;
    }
  }
  
  private double[] getPhotonBeamXYPos(Beam beam) {
    double[] xyPos = new double[2];
    double RNDx = Math.random();
    double beamX = beam.getBeamX()*1000;
//    previousX = (RNDx * XDimension) - (XDimension/2); //places on sample
    xyPos[0] = (RNDx * beamX) - (beamX/2); //places in beam area
    
    double RNDy = Math.random();
    double beamY = beam.getBeamY()*1000;
//    previousY = (RNDy * YDimension) - (YDimension/2);
    if (beam.getIsCircular()) {   //reduce Y limits so you can't put it out of the circle / ellipse
      double fractionLimit = Math.pow(1 - Math.pow(xyPos[0]/beamX, 2), 0.5);
      RNDy *= fractionLimit;
    }
    xyPos[1] = (RNDy * beamY) - (beamY/2);
    
    return xyPos;
  }
  
  private Map<Element, double[]> getRelativeShellProbs(Map<Element, Double> elementAbsorptionProbs, double beamEnergy){
    Map<Element, double[]> ionisationProbs = new HashMap<Element, double[]>();
    for (Element e : elementAbsorptionProbs.keySet()) {
      e.EdgeRatio();
      double runningSumProb = 0;
      double kshellProb = 0, L1shellProb = 0, L2shellProb = 0, L3shellProb = 0, M1shellProb = 0, M2shellProb = 0, M3shellProb = 0, M4shellProb = 0, M5shellProb = 0;
      double[] shellProbs = new double[9];
  //    double shellProb = 0;
      if (beamEnergy > e.getKEdge() ) {
        kshellProb = e.getKShellIonisationProb();
        runningSumProb += kshellProb;
        shellProbs[0] = runningSumProb;
      }
      if (beamEnergy > e.getL1Edge() && e.getAtomicNumber() >= 12) {
        L1shellProb = e.getL1ShellIonisationProb() * (1-kshellProb);
        runningSumProb += L1shellProb;
        shellProbs[1] = runningSumProb;
      }
      if (beamEnergy > e.getL2Edge() && e.getAtomicNumber() >= 12) {
        L2shellProb = e.getL2ShellIonisationProb() * (1-kshellProb-L1shellProb);
        runningSumProb += L2shellProb;
        shellProbs[2] = runningSumProb;
      }
      if (beamEnergy > e.getL3Edge() && e.getAtomicNumber() >= 12) {
        L3shellProb = e.getL3ShellIonisationProb() * (1-kshellProb-L1shellProb-L2shellProb);
        runningSumProb += L3shellProb;
        shellProbs[3] = runningSumProb;
      }
      if (beamEnergy > e.getM1Edge() && e.getAtomicNumber() >= 73) { 
        M1shellProb = e.getM1ShellIonisationProb() * (1-kshellProb-L1shellProb-L2shellProb-L3shellProb);
        runningSumProb += M1shellProb;
        shellProbs[4] = runningSumProb;
      }
      if (beamEnergy > e.getM2Edge() && e.getAtomicNumber() >= 73) { 
        M2shellProb = e.getM2ShellIonisationProb() * (1-kshellProb-L1shellProb-L2shellProb-L3shellProb-M1shellProb);
        runningSumProb += M2shellProb;
        shellProbs[5] = runningSumProb;
      }
      if (beamEnergy > e.getM3Edge() && e.getAtomicNumber() >= 73) { 
        M3shellProb = e.getM3ShellIonisationProb() * (1-kshellProb-L1shellProb-L2shellProb-L3shellProb-M1shellProb-M2shellProb);
        runningSumProb += M3shellProb;
        shellProbs[6] = runningSumProb;
      }
      if (beamEnergy > e.getM4Edge() && e.getAtomicNumber() >= 73) { 
        M4shellProb = e.getM4ShellIonisationProb() * (1-kshellProb-L1shellProb-L2shellProb-L3shellProb-M1shellProb-M2shellProb-M3shellProb);
        runningSumProb += M4shellProb;
        shellProbs[7] = runningSumProb;
      }
      if (beamEnergy > e.getM5Edge() && e.getAtomicNumber() >= 73) { 
        M4shellProb = e.getM5ShellIonisationProb() * (1-kshellProb-L1shellProb-L2shellProb-L3shellProb-M1shellProb-M2shellProb-M3shellProb-M4shellProb);
        runningSumProb += M4shellProb;
        shellProbs[8] = runningSumProb;
      }
      ionisationProbs.put(e, shellProbs);
    }
    return ionisationProbs;
  }
  
  private void populateAngularEmissionProbs() {
    angularEmissionProbs = new double[numberAngularEmissionBins];
  //    double photoelectric = coefCalc.getElementAbsorptionCoef(beam.getPhotonEnergy(), e);
      //integrate under the whole curve
      double lastHeight = 0;
      double totalArea = 0;
      for (int i = 0; i <= 100; i++) {
        double angle = ((Math.PI)/100)*i;
        double height = solvePolarisationEquationForAngle(angle, 1, 2);
        if (i > 0) {
          double area = ((lastHeight + height)/2) * ((Math.PI)/100);
          totalArea += area;
        }
        lastHeight = height;
      }
      //now get the proportion of some of these
   //   double[] emissionProbs = new double[numberAngularEmissionBins];
      lastHeight = 0;
      double cumulativeProb = 0;
      for (int i = 0; i <= numberAngularEmissionBins; i++) {
        double angle = ((Math.PI)/numberAngularEmissionBins)*i;
        double height = solvePolarisationEquationForAngle(angle, 1, 2);
        if (i > 0) {
          double area = ((lastHeight + height)/2) * ((Math.PI)/numberAngularEmissionBins);
          cumulativeProb += area / totalArea;
          angularEmissionProbs[i-1] = cumulativeProb;
        }
        lastHeight = height;
      }
    //  angularEmissionProbs.put(e, emissionProbs);
  
  }
  
  private double solvePolarisationEquationForAngle(double phi, double photoElectric, double beta) {
    double height = (photoElectric / (4*Math.PI)) * (1+(beta*0.5*(3*Math.pow(Math.cos(phi), 2) - 1)));
    return height;
  }
  
  private double getShellBindingEnergy(Element collidedElement, int collidedShell) {
    double shellBindingEnergy = 0;
    switch (collidedShell) {
      case 0: shellBindingEnergy = collidedElement.getKEdge();
              break;
      case 1: shellBindingEnergy = collidedElement.getL1Edge();
              break;
      case 2: shellBindingEnergy = collidedElement.getL2Edge();
              break;
      case 3: shellBindingEnergy = collidedElement.getL3Edge();
              break;
      case 4: shellBindingEnergy = collidedElement.getM1Edge();
              break;
      case 5: shellBindingEnergy = collidedElement.getM2Edge();
              break;
      case 6: shellBindingEnergy = collidedElement.getM3Edge();
              break;
      case 7: shellBindingEnergy = collidedElement.getM4Edge();
              break;
      case 8: shellBindingEnergy = collidedElement.getM5Edge();
              break;
    }
    return shellBindingEnergy;
  }
  
  private double getPrimaryElasticScatteringAngle(double electronEnergy, int atomicNumber){
    boolean highEnergy = false;
    if (electronEnergy > 20) {
      highEnergy = true;
    }
   
    //determine if need to get data from file or it's already loaded
    boolean getFile = mapPopulated(highEnergy, atomicNumber);
    
    //get the right file if I need to
    if (getFile == true) {
      
      TreeMap<Double, double[]> elementData = new TreeMap<Double, double[]>();
      try {
        elementData =  getAngleFileData(highEnergy, atomicNumber);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } 
      //now add the file data to the global array
      if (highEnergy == true) {
        highEnergyAngles[atomicNumber] = elementData;
      }
      else {
        lowEnergyAngles[atomicNumber] = elementData;
      }
    }
    
    //Now use the data in the global array to work out the angle
    //get nearest energy
    Double energyKey = returnNearestEnergy(highEnergy, atomicNumber, electronEnergy);
    
    //should probably interpolate the values here tbh.... will do at some point
    
    //get the differential cross sections for that energy of the element
    double[] energyAngleProbs = null;
    if (highEnergy == true) {
      energyAngleProbs = highEnergyAngles[atomicNumber].get(energyKey);
    }
    else {
      energyAngleProbs = lowEnergyAngles[atomicNumber].get(energyKey);
    }
    //get the angle from this 
    double deflectionAngle = returnDeflectionAngle(highEnergy, energyAngleProbs);
    
    if (Double.isNaN(deflectionAngle)){
      System.out.println("test");
    }
    return deflectionAngle;
  }
  
  private InputStreamReader locateFile(String filePath) 
      throws UnsupportedEncodingException, FileNotFoundException{
    InputStream is = getClass().getResourceAsStream("/" + filePath);

    if (is == null) {
      is = new FileInputStream(filePath);
    }

    return new InputStreamReader(is, "US-ASCII");
  }

  private boolean mapPopulated(boolean highEnergy, int atomicNumber) {
    if (highEnergy == true) {
      if (highEnergyAngles[atomicNumber] == null) {
        return true;
      }
      else {
        return false;
      }
    }
    else {
      if (lowEnergyAngles[atomicNumber] == null) {
        return true;
      }
      else {
       return false;
      }
    }
  }

//--put it in here when I have copy and paste back
private TreeMap<Double, double[]> getAngleFileData(boolean highEnergy, int atomicNum) throws IOException{
String elementNum = String.valueOf(atomicNum) + ".csv";
String filePath = "";
if (highEnergy == true) {
filePath = "constants/above_20000/" + elementNum;
}
else {
filePath = "constants/below_20000/" + elementNum;
}

InputStreamReader isr = locateFile(filePath);
BufferedReader br = new BufferedReader(isr);
TreeMap<Double, double[]> elementData = new TreeMap<Double, double[]>();
String line;
String[] components;
int count = -1;
while ((line = br.readLine()) != null) {
count +=1 ;
components = line.split(",");
if (count > 0) { //if this is not the first line
  Double energy = Double.valueOf(components[0]);
  String[] angleProbsString = Arrays.copyOfRange(components, 1, components.length);
  double[] angleProbs = new double[angleProbsString.length];
  for (int i = 0; i < angleProbsString.length; i++) {
    angleProbs[i] = Double.parseDouble(angleProbsString[i]);
  }
  //Now add this to the local treemap
  elementData.put(energy, angleProbs);
}
}
return elementData;
}


private Double returnNearestEnergy(boolean highEnergy, int atomicNumber, double electronEnergy) {
Double nearestEnergy = 0.;
if (electronEnergy >= 0.05 && electronEnergy <= 300) {
Double beforeKey = 0.;
Double afterKey = 0.;
if (highEnergy == true) {
  beforeKey = highEnergyAngles[atomicNumber].floorKey(electronEnergy);
  afterKey = highEnergyAngles[atomicNumber].ceilingKey(electronEnergy);
  
}
else {
  beforeKey = lowEnergyAngles[atomicNumber].floorKey(electronEnergy);
  afterKey = lowEnergyAngles[atomicNumber].ceilingKey(electronEnergy);
}
if (beforeKey == null) {
  beforeKey = 0.;
}
if (afterKey == null) {
  afterKey = 0.;
}
beforeKey = (beforeKey == 0.) ? afterKey: beforeKey;
afterKey = (afterKey == 0.) ? beforeKey: afterKey;
if (Math.abs(electronEnergy - beforeKey) <= Math.abs(electronEnergy-afterKey)) {
  nearestEnergy = beforeKey;
}
else {
  nearestEnergy = afterKey;
}

}
return nearestEnergy;
}

private double returnDeflectionAngle(boolean highEnergy, double[] energyAngleProbs) {
double totalProb = 0;
for (int i = 0; i < energyAngleProbs.length; i++) {
totalProb += energyAngleProbs[i];
}
double[] probPerAngle = new double[energyAngleProbs.length];
double sumProb = 0;
for (int j = 0; j < energyAngleProbs.length; j++) {
sumProb += energyAngleProbs[j];
probPerAngle[j] = sumProb/totalProb;
}

double RND = Math.random();
double index = 0;
for (int k = 0; k < probPerAngle.length; k++) {
if (probPerAngle[k] >= RND) {
  index = k;
  break;
}
}
//convert the index to an angle
double angleDegrees = 0;
if (highEnergy == true) {
double startFactor = 0.;
int factor = 0;
double divideFactor = 4;
double minusFactor = 0;
double modFactor = 0;
if (index >=1 && index < 146) {
  minusFactor = 1;
  modFactor = 36;
  factor = (int) ((int) (index - minusFactor)/modFactor);
  startFactor = Math.pow(10, factor) * 0.0001;
  divideFactor = 4;
}
else if (index >= 146 && index < 236) {
//   factor = (int) (index-146)/100;
  startFactor = 1;
  divideFactor = 10;
  minusFactor = 146;
  modFactor = 90;
}
else if (index >= 236 && index <= 296) {
  startFactor = 10;  //go until 25
  divideFactor = 40;
  minusFactor = 236;
  modFactor = 60;
}
else if (index > 296) {
  startFactor = 25;
  divideFactor = 50;
  minusFactor = 296;
  modFactor = 1000000; //just anything super high as all but first one
}
angleDegrees = startFactor + (((index-minusFactor)%modFactor)*(startFactor/divideFactor));
if (Double.isNaN(angleDegrees)){
//   System.out.println("test");
  angleDegrees = 0;
}
}
else {
angleDegrees = 1.0 * index;
}
double angleRadians = angleDegrees * Math.PI/180;
/*
if (index > 296 && highEnergy == true) {
System.out.println("test");
}
*/

return angleRadians;
}
  
  private boolean isMicrocrystalAt(final double x, final double y, final double z) {
    //Note that this is absolutely only right for a cuboid at the moment
    //This can stay as a quick test
    //this quick test actually messes with the program and it's imperfect placing of pixels
    
    if ((x > XDimension/2) || (x < -XDimension/2)) {
      return false;
    }
    if ((y > YDimension/2) || (y < -YDimension/2)) {
      return false;
    }
    if ((z > ZDimension/2) || (z < -ZDimension/2)) {
      return false;
    }
     
    //now do the crystal occupancy stuff
    //convert xyz to ijk
    
    int[] pixelCoords = convertToPixelCoordinates(x, y, z); 
    
    boolean[] occ = crystOccXFEL[pixelCoords[0]][pixelCoords[1]][pixelCoords[2]];  //This means that if has already been done don't do it again
                                          // Really needed to speed up Monte Carlo

    if (!occ[0]) {
      occ[1] = calculateCrystalOccupancy(x, y, z);
      occ[0] = true;
    }

    return occ[1];
  }
  
  private int[] convertToPixelCoordinates(final double x, final double y, final double z) {
    double[] xMinMax = this.minMaxVertices(0, verticesXFEL);
    double[] yMinMax = this.minMaxVertices(1, verticesXFEL);
    double[] zMinMax = this.minMaxVertices(2, verticesXFEL);
    int i = (int) StrictMath.round(((x/1000) - xMinMax[0]) * crystalPixPerUMXFEL);
    int j = (int) StrictMath.round(((y/1000) - yMinMax[0]) * crystalPixPerUMXFEL);
    int k = (int) StrictMath.round(((z/1000) - zMinMax[0]) * crystalPixPerUMXFEL);
    int[] pixelCoords = {i, j, k};
    return pixelCoords;
  }
  
  public boolean calculateCrystalOccupancy(final double x, final double y, final double z)
  {
    if (normals == null) {
      calculateNormals(false);
    }

    boolean inside = false;

    double[] directionVector = { 0, 0, 1 };
    double[] origin = new double[3];
    origin[0] = x/1000;
    origin[1] = y/1000;
    origin[2] = z/1000;
    //It doesn't work if x = y so need a fudge here... this is horrible.
    if (origin[0] == origin[1]) {
      origin[0] += 0.00001;
    }

    for (int l = 0; l < indicesXFEL.length; l++) {
      double intersectionDistance = Vector.rayTraceDistance(normals[l],
          directionVector, origin, originDistances[l]);

      Double distanceObject = Double.valueOf(intersectionDistance);

      if (intersectionDistance < 0 || distanceObject.isNaN()
          || distanceObject.isInfinite()) {
        continue;
      }

      double[] intersectionPoint = Vector.rayTraceToPointWithDistance(
          directionVector, origin, intersectionDistance);

      double[][] triangleVertices = new double[3][3];

      // copy vertices referenced by indices into single array for
      // passing onto the polygon inclusion test.
      for (int m = 0; m < 3; m++) {
        System.arraycopy(verticesXFEL[indicesXFEL[l][m] - 1], 0, triangleVertices[m],
            0, 3);
      }

      boolean crosses = Vector.polygonInclusionTest(triangleVertices,
          intersectionPoint);

      if (crosses) {
        inside = !inside;
      }
    }
    return inside;
  }
  
  /**
   * Returns the minimum and maximum values of a vertex array
   * given chosen dimension (0 = x, 1 = y, 2 = z).
   *
   * @param dimension 0 = x, 1 = y, 2 = z
   * @param vertices vertices to be examined
   * @return double array, first element minimum, second element maximum
   */
  public double[] minMaxVertices(final int dimension, final double[][] vertices) {

    double min = java.lang.Double.POSITIVE_INFINITY;
    double max = java.lang.Double.NEGATIVE_INFINITY;

    for (int i = 0; i < vertices.length; i++) {
      if (vertices[i][dimension] < min) {
        min = vertices[i][dimension];
      }

      if (vertices[i][dimension] > max) {
        max = vertices[i][dimension];
      }
    }

    double[] result = { min, max };

    return result;
  }
  
  private double getIntersectionDistance(double x, double y, double z, double ca, double cb, double cc) {
    if (normals == null) {
      calculateNormals(false);
    }

    double[] directionVector = {ca, cb, cc}; //the actual direction vector
    double minIntersect = 0;
    double[] origin = new double[3];
    origin[0] = x/1000;
    origin[1] = y/1000;
    origin[2] = z/1000;
    
    double intersectionDistance = 0;
    for (int l = 0; l < indicesXFEL.length; l++) {
      intersectionDistance = Vector.rayTraceDistance(normals[l],
          directionVector, origin, originDistances[l]);

      Double distanceObject = Double.valueOf(intersectionDistance);

      if (intersectionDistance < 0 || distanceObject.isNaN()
          || distanceObject.isInfinite()) {
          //do nothing
      }
      else {
    //    break; //maybe should just be closest, or an issue with the rayTRace
        if (minIntersect == 0) {
          minIntersect = intersectionDistance;
        }
        else {
          double min = Math.min(minIntersect, intersectionDistance);
          minIntersect = min;
        }
      }

    }
    return minIntersect;
  }
  
  /**
   * Calculates normal array from index and vertex arrays.
   * Also calculates signed distances of each triangle
   * from the origin.
   */
  public void calculateNormals(final boolean rotated) {

    double[][] verticesUsed = verticesXFEL;
    double[] originDistancesUsed = new double[verticesXFEL.length];
    double[][] normalsUsed = new double[verticesXFEL.length][3];

    normalsUsed = new double[indicesXFEL.length][3];
    originDistancesUsed = new double[indicesXFEL.length];

    for (int i = 0; i < indicesXFEL.length; i++) {
      // get the three vertices which this triangle corresponds to.
      double[] point1 = verticesUsed[indicesXFEL[i][0] - 1];
      double[] point2 = verticesUsed[indicesXFEL[i][1] - 1];
      double[] point3 = verticesUsed[indicesXFEL[i][2] - 1];

      // get two vectors which can be used to define our plane.

      double[] vector1 = Vector.vectorBetweenPoints(point1, point2);
      double[] vector2 = Vector.vectorBetweenPoints(point1, point3);

      // get the normal vector between these two vectors.

      double[] normalVector = Vector.normalisedCrossProduct(vector1, vector2);

      // copy this vector into the normals array at the given point.
      System.arraycopy(normalVector, 0, normalsUsed[i], 0, 3);

      double distanceFromOrigin = -(normalVector[0] * point1[0]
          + normalVector[1] * point1[1] + normalVector[2] * point1[2]);

      originDistancesUsed[i] = distanceFromOrigin;
    }

      originDistances = new double[indicesXFEL.length];
      normals = new double[indicesXFEL.length][3];

      for (int i = 0; i < normalsUsed.length; i++) {
        System.arraycopy(normalsUsed[i], 0, normals[i], 0, 3);
      }

      System.arraycopy(originDistancesUsed, 0, originDistances, 0,
          indicesXFEL.length);
    
  }
  
  private static class Vector {
    /**
     * Returns magnitude of 3D vector.
     *
     * @param vector 3d coordinates of vector
     * @return magnitude scalar.
     */
    public static double vectorMagnitude(final double[] vector) {
      double squaredDistance = Math.pow(vector[0], 2) + Math.pow(vector[1], 2)
          + Math.pow(vector[2], 2);

      double distance = Math.sqrt(squaredDistance);

      return distance;
    }

    /**
     * returns 3D vector between FROM and TO points.
     *
     * @param from from point
     * @param to to point
     * @return vector between points.
     */
    public static double[] vectorBetweenPoints(final double[] from,
        final double[] to) {
      double[] newVector = new double[3];

      for (int i = 0; i < 3; i++) {
        newVector[i] = to[i] - from[i];
      }

      return newVector;
    }
    
    public static double[] normaliseVector(final double[] vector) {
      double[] newVector = new double[3];
      double magnitude = vectorMagnitude(vector);
      
      for (int i = 0; i < 3; i++) {
        newVector[i] = vector[i]/magnitude;
      }
      
      return newVector;
    }

    /**
     * returns 3D cross-product between two vectors.
     *
     * @param vector1 vector1
     * @param vector2 vector2
     * @return cross product
     */
    public static double[] crossProduct(final double[] vector1,
        final double[] vector2) {
      double[] newVector = new double[3];

      newVector[0] = vector1[1] * vector2[2] - vector1[2] * vector2[1];
      newVector[1] = vector1[2] * vector2[0] - vector1[0] * vector2[2];
      newVector[2] = vector1[0] * vector2[1] - vector1[1] * vector2[0];

      return newVector;
    }

    /**
     * returns 3D cross product with magnitude set to 1 between
     * two vectors.
     *
     * @param vector1 vector1
     * @param vector2 vector2
     * @return normalised cross product
     */
    public static double[] normalisedCrossProduct(final double[] vector1,
        final double[] vector2) {
      double[] newVector = crossProduct(vector1, vector2);
      double magnitude = vectorMagnitude(newVector);

      for (int i = 0; i < 3; i++) {
        newVector[i] /= magnitude;
      }

      return newVector;
    }

    /**
     * returns dot product between two 3D vectors.
     *
     * @param vector1 vector1
     * @param vector2 vector2
     * @return dot product
     */
    public static double dotProduct(final double[] vector1,
        final double[] vector2) {
      double dotProduct = 0;

      for (int i = 0; i < 3; i++) {
        dotProduct += vector1[i] * vector2[i];
      }

      return dotProduct;
    }

    /**
     * Ray trace from a point to a plane via a direction vector,
     * find the intersection between the direction vector and the
     * plane and return this point.
     *
     * @param normalUnitVector normal vector with magnitude 1
     * @param directionVector direction vector of any magnitude
     * @param origin point from which ray is traced (i.e. voxel coordinate)
     * @param planeDistance distance of plane from true origin (0, 0, 0)
     * @return intersection point between plane and direction vector
     */
    @SuppressWarnings("unused")
    public static double[] rayTraceToPoint(final double[] normalUnitVector,
        final double[] directionVector, final double[] origin,
        final double planeDistance) {
      double t = rayTraceDistance(normalUnitVector, directionVector, origin,
          planeDistance);

      double[] point = new double[3];

      for (int i = 0; i < 3; i++) {
        point[i] = origin[i] + t * directionVector[i];
      }

      return point;
    }

    /**
     * Ray trace - find intersection of direction vector from point
     * with plane from already-known distance t.
     *
     * @param directionVector direction vector
     * @param origin point from which ray is traced
     * @param t distance of origin to plane along direction vector
     * @return point of intersection
     */
    public static double[] rayTraceToPointWithDistance(
        final double[] directionVector,
        final double[] origin,
        final double t) {
      double[] point = new double[3];

      for (int i = 0; i < 3; i++) {
        point[i] = origin[i] + t * directionVector[i];
      }

      return point;
    }

    /**
     * Ray trace from a point to a plane via a direction vector,
     * find the signed distance between the direction vector and
     * the plane and return this point.
     *
     * @param normalUnitVector normal vector with magnitude 1
     * @param directionVector direction vector of any magnitude
     * @param origin point from which ray is traced (i.e. voxel coordinate)
     * @param planeDistance distance of plane from true origin (0, 0, 0)
     * @return signed distance between direction vector and plane
     */
    public static double rayTraceDistance(final double[] normalUnitVector,
        final double[] directionVector, final double[] origin,
        final double planeDistance) {

      double originNormalDotProduct = dotProduct(origin, normalUnitVector);
      double directionNormalDotProduct = dotProduct(directionVector,
          normalUnitVector);

      double t = -(originNormalDotProduct + planeDistance)
          / directionNormalDotProduct;

      return t;
    }

    /**
     * Original C code
     * http://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html
     * Takes an array of vertices of a polygon and determines whether a point
     * is contained within the polygon or not. Ignores the z axis at the
     * moment.
     *
     * @param vertices array of 3D vertices
     * @param point point to test inclusion - must be in same plane
     *          as vertices
     * @return boolean value - in polygon or not in polygon.
     */
    public static boolean polygonInclusionTest(final double[][] vertices,
        final double[] point) {
      boolean c = false;

      for (int i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
        if (((vertices[i][1] > point[1]) != (vertices[j][1] > point[1]))
            && (point[0] < (vertices[j][0] - vertices[i][0])
                * (point[1] - vertices[i][1])
                / (vertices[j][1] - vertices[i][1]) + vertices[i][0])) {
          c = !c;
        }
      }

      return c;
    }
  }

}
