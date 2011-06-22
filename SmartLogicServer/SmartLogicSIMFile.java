/**  
* SmartLogicSIMFile.java - Manages SIM file hierarchy  
* @author  Gerhard de Koning Gans
*/ 

class SmartLogicSIMFile {
	private String MF;
	private String DF;
	private String EF;

	public SmartLogicSIMFile() {
		MF = "XXXX";
		DF = "XXXX";
		EF = "XXXX";
	}

	public void reset() {
		MF = "XXXX";
		DF = "XXXX";
		EF = "XXXX";
	}

	public String getMF() {
		return MF;
	}

	public String getDF() {
		return DF;
	}

	public String getEF() {
		return EF;
	}

	public void setMF(String newMF) {
		if (newMF.length() != 4) {
			MF = "XXXX";
		} else {
			MF = newMF.toUpperCase();
		}
	}

	public void setDF(String newDF) {
		if (newDF.length() != 4) {
			DF = "XXXX";
		} else {
			DF = newDF.toUpperCase();
		}
	}

	public void setEF(String newEF) {
		if (newEF.length() != 4) {
			EF = "XXXX";
		} else {
			EF = newEF.toUpperCase();
		}
	}

	public void selectFile(String SF) {
		if (SF.equals("3F00")) {
			MF = "3F00";
			DF = "XXXX";
			EF = "XXXX";
		} else if (SF.substring(0, 2).equals("7F")) {
			DF = SF;
			EF = "XXXX";
		} else {
			EF = SF;
		}
	}

	// Returns identifier that needs to be selected on SIM
	// Returns empty string if already in correct file
	public String changeFile(SmartLogicSIMFile sFile) {
		String APDU = "";
		if (sFile.getMF().equals(MF)) {
			// MasterFile is okay
			if (sFile.getDF().equals(DF)) {
				// DirectoryFile is okay
				if (!sFile.getEF().equals(EF)) {
					EF = sFile.getEF();
					if (EF.equals("XXXX")) {
						if (!DF.equals("XXXX")) {
							APDU = DF.substring(0, 2) + " "
									+ DF.substring(2, 4);
						}
					} else {
						APDU = EF.substring(0, 2) + " " + EF.substring(2, 4);
					}
				}
			} else {
				DF = sFile.getDF();
				EF = "XXXX";
				if (DF.equals("XXXX")) {
					if (!MF.equals("XXXX")) {
						APDU = MF.substring(0, 2) + " " + MF.substring(2, 4);
					}
				} else {
					APDU = DF.substring(0, 2) + " " + DF.substring(2, 4);
				}
			}
		} else {
			MF = sFile.getMF();
			DF = "XXXX";
			EF = "XXXX";
			APDU = MF.substring(0, 2) + " " + MF.substring(2, 4);
		}

		if (APDU.length() > 0) {
			APDU = "A0 A4 00 00 02 " + APDU;
		}

		return APDU;
	}

	public boolean equals(SmartLogicSIMFile sFile) {
		if (sFile.getMF().equals(MF) && sFile.getDF().equals(DF)
				&& sFile.getEF().equals(EF)) {
			return true;
		} else {
			return false;
		}
	}

	public String toString() {
		return MF + DF + EF;
	}
}
