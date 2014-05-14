package se.raddo.raddose3D;

import java.util.Map;

import se.raddo.raddose3D.ElementDatabase.DatabaseFields;

/**
 * The Element class contains physical constants of an element associated with
 * x-ray cross sections.
 */
public class Element {
  /**
   * Element name.
   */
  public final String                                       elementName;

  /**
   * Atomic number.
   */
  public final int                                          atomicNumber;

  /**
   * Stored information about the chemical element.
   */
  private final Map<ElementDatabase.DatabaseFields, Double> elementData;

  /**
   * List of absorption edges.
   */
  private enum AbsorptionEdge {
    K, L, M, N, C, I
  }

  /** Atomic mass unit in grams. */
  private static final double ATOMIC_MASS_UNIT          = 1.66E-24;

  /** LJ_1 variable from Fortran, used to correct atomic elements < 29 Z. */
  private static final double LJ_1                      = 1.160;

  /** LJ_2 variable from Fortran, used to correct atomic elements < 29 Z. */
  private static final double LJ_2                      = 1.41;

  /** Light atom/heavy atom threshold. */
  public static final int     LIGHT_ATOM_MAX_NUM        = 29;

  /** Absorption edge room for error. */
  private static final double ABSORPTION_EDGE_TOLERANCE = 0.001;

  /** Number of expansions of the polynomial. */
  private static final int    POLYNOMIAL_EXPANSION      = 4;

  /** Occurrences - number of times this atom is found in the protein. */
  @Deprecated
  private double              macromolecularOccurrence;

  /**
   * Hetatms - number of times this atom is found in the protein, should also
   * be included in macromolecular occurrence.
   */
  @Deprecated
  private double              hetatmOccurrence          = 0;

  /**
   * Concentration of this atom in the solvent.
   */
  @Deprecated
  private double              solventConcentration;

  /**
   * Number of atoms of this type in the solvent,
   * calculated from solvent concentration.
   */
  @Deprecated
  private double              solventOccurrence;

  /**
   * calculated cross-sections.
   */
  private double              photoelectricCrossSection,
                              totalCrossSection,
                              coherentCrossSection;

  /**
   * Create a new element with name, atomic number and associated information.
   * 
   * @param element
   *          element name
   * @param protons
   *          atomic number
   * @param elementInformation
   *          Map containing the associated element information
   */
  public Element(final String element, final int protons,
      final Map<ElementDatabase.DatabaseFields, Double> elementInformation) {
    elementName = element;
    atomicNumber = protons;
    elementData = elementInformation;
  }

  /**
   * Total atoms combining solvent occurrence and macromolecular occurrence.
   * 
   * @return total atoms in unit cell
   */
  public double totalAtoms() {
    double totalAtoms = this.solventOccurrence
        + this.macromolecularOccurrence;

    return totalAtoms;
  }

  /**
   * multiplies total atoms in unit cell by atomic weight.
   * 
   * @return
   *         total weight of atoms in unit cell
   */
  public double totalMass() {
    return getAtomicWeight() * totalAtoms() * ATOMIC_MASS_UNIT;
  }

  /**
   * Returns the correct edge coefficient depending on the coefficient number
   * and the edge specified.
   * 
   * @param num number coefficient (0, 1, 2, 3) to access
   * @param edge String indicating which edge coefficient (K, L, M, N, C, I).
   * @return corresponding edge coefficient.
   */
  private double edgeCoefficient(final int num, final String edge) {
    switch (edge.toCharArray()[0]) {
      case 'K':
        switch (num) {
          case 0:
            return elementData.get(ElementDatabase.DatabaseFields.K_COEFF_0);
          case 1:
            return elementData.get(ElementDatabase.DatabaseFields.K_COEFF_1);
          case 2:
            return elementData.get(ElementDatabase.DatabaseFields.K_COEFF_2);
          case 3:
            return elementData.get(ElementDatabase.DatabaseFields.K_COEFF_3);
        }
      case 'L':
        switch (num) {
          case 0:
            return elementData.get(ElementDatabase.DatabaseFields.L_COEFF_0);
          case 1:
            return elementData.get(ElementDatabase.DatabaseFields.L_COEFF_1);
          case 2:
            return elementData.get(ElementDatabase.DatabaseFields.L_COEFF_2);
          case 3:
            return elementData.get(ElementDatabase.DatabaseFields.L_COEFF_3);
        }
      case 'M':
        switch (num) {
          case 0:
            return elementData.get(ElementDatabase.DatabaseFields.M_COEFF_0);
          case 1:
            return elementData.get(ElementDatabase.DatabaseFields.M_COEFF_1);
          case 2:
            return elementData.get(ElementDatabase.DatabaseFields.M_COEFF_2);
          case 3:
            return elementData.get(ElementDatabase.DatabaseFields.M_COEFF_3);
        }
      case 'N':
        switch (num) {
          case 0:
            return elementData.get(ElementDatabase.DatabaseFields.N_COEFF_0);
          case 1:
            return elementData.get(ElementDatabase.DatabaseFields.N_COEFF_1);
          case 2:
            return elementData.get(ElementDatabase.DatabaseFields.N_COEFF_2);
          case 3:
            return elementData.get(ElementDatabase.DatabaseFields.N_COEFF_3);
        }
      case 'C':
        switch (num) {
          case 0:
            return elementData
                .get(ElementDatabase.DatabaseFields.COHERENT_COEFF_0);
          case 1:
            return elementData
                .get(ElementDatabase.DatabaseFields.COHERENT_COEFF_1);
          case 2:
            return elementData
                .get(ElementDatabase.DatabaseFields.COHERENT_COEFF_2);
          case 3:
            return elementData
                .get(ElementDatabase.DatabaseFields.COHERENT_COEFF_3);
        }
      case 'I':
        switch (num) {
          case 0:
            return elementData
                .get(ElementDatabase.DatabaseFields.INCOHERENT_COEFF_0);
          case 1:
            return elementData
                .get(ElementDatabase.DatabaseFields.INCOHERENT_COEFF_1);
          case 2:
            return elementData
                .get(ElementDatabase.DatabaseFields.INCOHERENT_COEFF_2);
          case 3:
            return elementData
                .get(ElementDatabase.DatabaseFields.INCOHERENT_COEFF_3);
        }
      default:
        System.out
            .println("ERROR: Something's gone horribly wrong in the code");
        return -1;
    }
  }

  /**
   * Calculation of "bax" for corresponding edge and energy in Angstroms.
   * 
   * @param energy
   *          energy of beam in Angstroms
   * @param edge
   *          String indicating which edge coefficient (K, L, M, N, C, I).
   * @return
   *         value of bax
   */
  public double baxForEdge(final double energy, final String edge) {
    // calculation from logarithmic coefficients in McMaster tables.

    double sum = 0;

    for (int i = 0; i < POLYNOMIAL_EXPANSION; i++) {
      double coefficient = edgeCoefficient(i, edge);

      if (coefficient == -1) {
        sum = 0;
      } else if (energy == 1) {
        sum += coefficient;
      } else {
        sum += coefficient * Math.pow(Math.log(energy), i);
      }
    }

    double bax = Math.exp(sum);

    return bax;
  }

  /**
   * energy is between two edges; this function finds the corresponding edge
   * and
   * calculates bax for this edge. Corrects bax if atomic number is below 29,
   * and then uses this to calculate the cross-sections.
   * 
   * @param energy wavelength in Angstroms
   */
  public void calculateMu(final double energy) {
    Double absorptionEdgeK =
        elementData.get(ElementDatabase.DatabaseFields.EDGE_K);
    Double absorptionEdgeL =
        elementData.get(ElementDatabase.DatabaseFields.EDGE_L);
    Double absorptionEdgeM =
        elementData.get(ElementDatabase.DatabaseFields.EDGE_M);

    if (energy < absorptionEdgeK
        && energy > absorptionEdgeK - ABSORPTION_EDGE_TOLERANCE) {
      System.out
          .println("Warning: using an energy close to middle of K edge of "
              + elementName);
      return;
    }
    if (energy < absorptionEdgeL
        && energy > absorptionEdgeL - ABSORPTION_EDGE_TOLERANCE) {
      System.out
          .println("Warning: using an energy close to middle of L edge of "
              + elementName);
      return;
    }
    if (energy < absorptionEdgeM
        && energy > absorptionEdgeM - ABSORPTION_EDGE_TOLERANCE) {
      System.out
          .println("Warning: using an energy close to middle of M edge of "
              + elementName);
      return;
    }

    double bax = 0;

    if (energy > absorptionEdgeK) {
      bax = baxForEdge(energy, "K");
    } else if (energy < absorptionEdgeK && energy > absorptionEdgeL) {
      bax = baxForEdge(energy, "L");
    } else if (energy < absorptionEdgeL && energy > absorptionEdgeM) {
      bax = baxForEdge(energy, "M");
    } else if (energy < absorptionEdgeM) {
      bax = baxForEdge(energy, "N");
    }

    // Fortran says...
    // correct for L-edges since McMaster uses L1 edge.
    // Use edge jumps for correct X-sections.

    if (atomicNumber <= LIGHT_ATOM_MAX_NUM) {
      if (energy > elementData.get(ElementDatabase.DatabaseFields.L3)
          && energy < elementData.get(ElementDatabase.DatabaseFields.L2)) {
        bax /= (LJ_1 * LJ_2);
      }

      if (energy > elementData.get(ElementDatabase.DatabaseFields.L2)
          && energy < absorptionEdgeL) {
        bax /= LJ_1;
      }
    }

    double bcox = 0;
    double binx = 0;

    if (elementData.get(ElementDatabase.DatabaseFields.COHERENT_COEFF_0) != 0) {
      bcox = baxForEdge(energy, "C");
    }

    if (elementData.get(ElementDatabase.DatabaseFields.INCOHERENT_COEFF_0) != 0)
    {
      binx = baxForEdge(energy, "I");
    }

    double btox = bax + bcox + binx;

    photoelectricCrossSection = bax; // mu, abs coefficient
    totalCrossSection = btox; // attenuation
    coherentCrossSection = bcox; // elastic
  }

  /**
   * @return the elementName
   */
  @Deprecated
  public String getElementName() {
    return elementName;
  }

  /**
   * @return the atomicNumber
   */
  @Deprecated
  public int getAtomicNumber() {
    return atomicNumber;
  }

  /**
   * @return the atomicWeight
   */
  public double getAtomicWeight() {
    return elementData.get(DatabaseFields.ATOMIC_WEIGHT);
  }

  /**
   * @return the macromolecularOccurrence
   */
  @Deprecated
  public double getMacromolecularOccurrence() {
    return macromolecularOccurrence;
  }

  /**
   * @param newmacromolecularOccurrence the macromolecularOccurrence to set
   */
  @Deprecated
  public void setMacromolecularOccurrence(
      final double newmacromolecularOccurrence) {
    this.macromolecularOccurrence = newmacromolecularOccurrence;
  }

  /**
   * @param increment the macromolecularOccurrence increment
   */
  @Deprecated
  public void incrementMacromolecularOccurrence(final double increment) {
    this.macromolecularOccurrence += increment;
  }

  /**
   * @return the hetatmOccurrence
   */
  @Deprecated
  public double getHetatmOccurrence() {
    return new Double(hetatmOccurrence);
  }

  /**
   * @param newhetatmOccurrence the hetatmOccurrence to set
   */
  @Deprecated
  public void setHetatmOccurrence(final double newhetatmOccurrence) {
    this.hetatmOccurrence = newhetatmOccurrence;
  }

  /**
   * @return the solventConcentration
   */
  @Deprecated
  public double getSolventConcentration() {
    return solventConcentration;
  }

  /**
   * @param newsolventConcentration the solventConcentration to set
   */
  @Deprecated
  public void setSolventConcentration(final double newsolventConcentration) {
    this.solventConcentration = newsolventConcentration;
  }

  /**
   * @param increment the solventConcentration to increment
   */
  @Deprecated
  public void incrementSolventConcentration(final double increment) {
    this.solventConcentration += increment;
  }

  /**
   * @return the solventOccurrence
   */
  @Deprecated
  public double getSolventOccurrence() {
    return solventOccurrence;
  }

  /**
   * @param newsolventOccurrence the solventOccurrence to set
   */
  @Deprecated
  public void setSolventOccurrence(final double newsolventOccurrence) {
    this.solventOccurrence = newsolventOccurrence;
  }

  /**
   * @param increment the solventOccurrence to increment
   */
  @Deprecated
  public void incrementSolventOccurrence(final double increment) {
    this.solventOccurrence += increment;
  }

  /**
   * @return the photoelectricCrossSection
   */
  public double getPhotoelectricCrossSection() {
    return photoelectricCrossSection;
  }

  /**
   * @return the totalCrossSection
   */
  public double getTotalCrossSection() {
    return totalCrossSection;
  }

  /**
   * @return the coherentCrossSection
   */
  public double getCoherentCrossSection() {
    return coherentCrossSection;
  }
}