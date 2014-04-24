package se.raddo.raddose3D;

import java.util.List;
import java.lang.Math;

import se.raddo.raddose3D.parser.MuCalcConstantParser;

/**
 * @author Helen Ginn
 */

public class CoefCalcCompute extends CoefCalc
{
  /**
   * Identified coefficients and density from last program run. Final variables.
   */
  private double                absCoeff, attCoeff, elasCoeff, density, cellVolume;

  public static final double    PI        =  3.141592653589793;
   
  /*
   * Protein, RNA, DNA densities and other constants
   */
  
  public static final double    PROTEIN_DENSITY     = 1.35; // g/ml
  public static final double    RNA_DENSITY         = 2.0;  // g/ml
  public static final double    DNA_DENSITY         = 2.0;  // g/ml
  public static final double    ATOMIC_MASS_UNIT    = 1.66E-24; // in grams
  public static final double    AVOGADRO_NUM        = 6.022e+23;
  public static final double    AMINO_ACID_AVE_MASS = 110.0;
  public static final double    DNA_NUCLEOTIDE_MASS = 312.0;
  public static final double    RNA_NUCLEOTIDE_MASS = 321.0;
  public static final double    ANGSTROMS_TO_ML     = 1E-24;
  public static final double    WATER_CONCENTRATION = 55555;
  private static final long UNITSPERMILLIUNIT   = 1000L;
  
  /*
   * Properties which will be updated when this class is used to calculate the coefficients.
   */
  
  public double                 macromolecularMass;
  
  
  /*
   * Our friendly parser which is going to look after our element constants for us.
   */
  
  private static MuCalcConstantParser  parser;
  
  public CoefCalcCompute() throws Exception
  {
    parser = new MuCalcConstantParser();
  }
  
  @Override
  public void updateCoefficients(final Wedge w, final Beam b)
  {
      // density is easy. Loop through all atoms and calculate total mass.
      // then express as g / cm-3.
      
      double mass = 0;
    
      for (int i=0; i < parser.atomCount; i++)
      {
        double addition = parser.atoms[i].totalMass();
        
        mass += addition;
      }
      
      density = mass * 1E-3 * 1E30 / (cellVolume * 1000);
    
      double energy = b.getPhotonEnergy();
      
      double crossSectionPhotoElectric = 0;
      double crossSectionCoherent = 0;
      double crossSectionTotal = 0;
      
      // take cross section contributions from each individual atom weighted by the cell volume
      
      for (int i=0; i < parser.atomCount; i++)
      {
        parser.atoms[i].calculateMu(energy);
       
        crossSectionPhotoElectric += parser.atoms[i].totalAtoms() * parser.atoms[i].photoelectricCrossSection * 0.1 / cellVolume;
        crossSectionCoherent += parser.atoms[i].totalAtoms() * parser.atoms[i].coherentCrossSection * 0.1 / cellVolume;
        crossSectionTotal += parser.atoms[i].totalAtoms() * parser.atoms[i].totalCrossSection * 0.1 / cellVolume;
      }
      
      absCoeff = crossSectionPhotoElectric / UNITSPERMILLIUNIT;
      attCoeff = crossSectionTotal / UNITSPERMILLIUNIT;
      elasCoeff = crossSectionCoherent / UNITSPERMILLIUNIT;
  }
  
  @Override
  public double getAbsorptionCoefficient()
  {
    return absCoeff;
  }

  @Override
  public double getAttenuationCoefficient()
  {
    return attCoeff;
  }

  @Override
  public double getElasCoef()
  {
    return elasCoeff;
  }

  @Override
  public double getDensity()
  {
    return density;
  }
  
  @Override
  public String toString()
  {
    return String.format(
        "Crystal coefficients calculated with Raddose-3D "
            + "(Paithankar et al., 2009). \n"
            + "Absorption Coefficient: %.2e /um.\n"
            + "Attenuation Coefficient: %.2e /um.\n"
            + "Elastic Coefficient: %.2e /um.\n"
            + "Density: %.2f g/ml.\n",
        absCoeff, attCoeff, elasCoeff, density);
  }

  /**
   * Compute results and put them in local variables absCoeff, attCoeff, elasCoeff and density.
   */
  public CoefCalcCompute(final Double cellA, final Double cellB,
      final Double cellC,
      final Double cellAlpha, final Double cellBeta, final Double cellGamma,
      final int numMonomers, final int numResidues, final int numRNA,
      final int numDNA,
      final List<String> heavyProteinAtomNames,
      final List<Double> heavyProteinAtomNums,
      final List<String> heavySolutionConcNames,
      final List<Double> heavySolutionConcNums,
      final Double solventFraction)
  {
    parser = new MuCalcConstantParser();
    
    Double alpha = cellAlpha;
    Double beta = cellBeta;
    Double gamma = cellGamma;    
    
    if (alpha == null)
      alpha = 90.0;
    if (beta == null)
      beta = 90.0;
    if (gamma == null)
      gamma = 90.0;
    
    
    double cellVolume = this.cellVolume(cellA, cellB, cellC, alpha, beta, gamma);
    
    this.calculateAtomOccurrences(numMonomers, numResidues, numRNA, numDNA, solventFraction, heavyProteinAtomNames, heavyProteinAtomNums, heavySolutionConcNames, heavySolutionConcNums, cellVolume);
    
  }
  
  /**
   * Calculate the macromolecular mass (etc.) and add the appropriate numbers of atom occurrences to the parser's atom array.
   */
  public void calculateAtomOccurrences(int numMonomers, int numResidues, int numRNA, int numDNA, double solventFraction, List<String> heavyProteinAtomNames, List<Double> heavyProteinAtomNums, List<String> heavySolvConcNames, List<Double> heavySolvConcNums, double cellVolume)
  {
    // Start by dealing with heavy atom in the protein and adding these to the unit cell.
    
    for (int i=0; i < heavyProteinAtomNames.size(); i++)
    {
       Atom heavyAtom = parser.findAtomWithName(heavyProteinAtomNames.get(i));
       
       // note: heavy atoms are provided per monomer, so multiply by number of monomers.
       heavyAtom.macromolecularOccurrence += heavyProteinAtomNums.get(i) * numMonomers;
    }

    // Combine concentrations of heavy atoms in the solvent and add these to the unit cell.
    
    for (int i=0; i < heavySolvConcNames.size(); i++)
    {
       Atom heavyAtom = parser.findAtomWithName(heavySolvConcNames.get(i));
       
       heavyAtom.solventConcentration += heavySolvConcNums.get(i);
    }
    
    // If the solvent fraction has not been specified.
    if (solventFraction < 0)
    {
      // Protein, RNA, DNA masses are calculated and then weighted to fit the unit cell.

      double protein_mass = ATOMIC_MASS_UNIT * AMINO_ACID_AVE_MASS * numResidues * numMonomers;
      protein_mass /= cellVolume * PROTEIN_DENSITY * ANGSTROMS_TO_ML;

      double RNA_mass = ATOMIC_MASS_UNIT * RNA_NUCLEOTIDE_MASS * numRNA * numMonomers;
      RNA_mass /= cellVolume * RNA_DENSITY * ANGSTROMS_TO_ML;

      double DNA_mass = ATOMIC_MASS_UNIT * DNA_NUCLEOTIDE_MASS * numDNA * numMonomers;
      DNA_mass /= cellVolume * DNA_DENSITY * ANGSTROMS_TO_ML;

      // We estimate the solvent fraction from the remaining mass to be found in the crystal. Magic!
      
      solventFraction = 1 - protein_mass - RNA_mass - DNA_mass;
      
      // sanity check
      if (solventFraction < 0)
        System.out.println("Warning: Solvent mass calculated as a negative number...");
      
      System.out.println("Solvent fraction determined as " + solventFraction * 100 + "%.");
    }
    
    // Convert solvent concentrations in unit cell to solvent no. of atoms (loop round all atoms)
    // Also need to know number of non-water atoms in the solvent in order to calculate a displacement.
    // 1 Angstrom = 1E-27 litres.
    
    double nonWaterAtoms = 0;
    
    for (int i=0; i < parser.atomCount; i++)
    {
      double conc = parser.atoms[i].solventConcentration;
      double atomCount = conc * 1E-3 * AVOGADRO_NUM * cellVolume * 1E-27 * solventFraction;
      parser.atoms[i].solventOccurrence += atomCount;
      
      nonWaterAtoms += atomCount;
    }
      
    // Calculating number of water molecules.
    // NOTE: using updated value for concentration of water, 55.555M instead of the 55M in Fortran.

    double waterMolecules = WATER_CONCENTRATION * 1E-3 * AVOGADRO_NUM * cellVolume * 1E-27 * solventFraction - nonWaterAtoms;
    
    // Add water molecules to hydrogen and oxygen.
    
    Atom hydrogen = parser.findAtomWithZ(1);
    hydrogen.solventOccurrence += waterMolecules * 2;
    
    Atom oxygen = parser.findAtomWithZ(8);
    oxygen.solventOccurrence += waterMolecules;
    
    // Atom preparation...
    
    Atom carbon = parser.findAtomWithZ(6);
    Atom nitrogen = parser.findAtomWithZ(7);
    Atom phosphorus = parser.findAtomWithZ(15);
    
    // Protein atoms: for every amino acid
    // add 5C + 1.35 N + 1.5 O + 8H
    
    carbon.macromolecularOccurrence += 5 * numResidues * numMonomers;
    nitrogen.macromolecularOccurrence += 1.35 * numResidues * numMonomers;
    oxygen.macromolecularOccurrence += 1.5 * numResidues * numMonomers;
    hydrogen.macromolecularOccurrence += 8 * numResidues * numMonomers;
    
    // RNA atoms: for every NTP
    // add 11.25 H + 9.5 C + 3.75 N + 7 O + 1 P.
    
    carbon.macromolecularOccurrence += 9.5 * numRNA * numMonomers;
    nitrogen.macromolecularOccurrence += 3.75 * numRNA * numMonomers;
    oxygen.macromolecularOccurrence += 7 * numRNA * numMonomers;
    hydrogen.macromolecularOccurrence += 11.25 * numRNA * numMonomers;
    phosphorus.macromolecularOccurrence += 1 * numRNA * numMonomers;
    
    // DNA atoms: for every NTP
    // add 11.75 H + 9.75 C + 4 N + 6 O + 1 P.
   
    carbon.macromolecularOccurrence += 9.75 * numRNA * numMonomers;
    nitrogen.macromolecularOccurrence += 4 * numRNA * numMonomers;
    oxygen.macromolecularOccurrence += 6 * numRNA * numMonomers;
    hydrogen.macromolecularOccurrence += 11.75 * numRNA * numMonomers;
    phosphorus.macromolecularOccurrence += 1 * numRNA * numMonomers;
  }

  /**
   * Calculate cell volume from cell dimensions and unit cell angles. Converted from Fortran code.
   */
 public double cellVolume(double cellA, double cellB, double cellC, double cellAlpha, double cellBeta, double cellGamma)
  {
    double alpha = cellAlpha * PI / 180;
    double beta = cellBeta * PI / 180;
    double gamma = cellGamma * PI / 180;
    
    double ult = 1.0 + 2.0 * Math.cos(alpha) * Math.cos(beta) * Math.cos(gamma) - Math.pow(Math.cos(alpha), 2.0) - Math.pow(Math.cos(beta), 2.0) - Math.pow(Math.cos(gamma), 2.0);
     
    if (ult < 0.0)
      System.out.println("Warning: error calculating unit cell volume - please check inputs.");
    
    System.out.println(cellA + ", " + cellB + ", " + cellC);
    
    double cellVol = cellA * cellB * cellC * Math.sqrt(ult);
    
    // This result below is what Fortran thought of a 78.27 x 78.27 x 78.27 (cubic) unit cell
    // instead of our value now of 479497.1 Angstroms cubed
    // resulting in an error between the calculations.
  //  double cellVol = 460286.7; Angstrom cubed
    
    cellVolume = cellVol;
    
    System.out.println("Cell volume: " + cellVolume + " Angstroms cubed");
    
    return cellVol;
  }
}