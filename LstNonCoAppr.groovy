/*
 ***************************************************************
 *                                                             *
 *                           NOTICE                            *
 *                                                             *
 *   THIS SOFTWARE IS THE PROPERTY OF AND CONTAINS             *
 *   CONFIDENTIAL INFORMATION OF INFOR AND/OR ITS AFFILIATES   *
 *   OR SUBSIDIARIES AND SHALL NOT BE DISCLOSED WITHOUT PRIOR  *
 *   WRITTEN PERMISSION. LICENSED CUSTOMERS MAY COPY AND       *
 *   ADAPT THIS SOFTWARE FOR THEIR OWN USE IN ACCORDANCE WITH  *
 *   THE TERMS OF THEIR SOFTWARE LICENSE AGREEMENT.            *
 *   ALL OTHER RIGHTS RESERVED.                                *
 *                                                             *
 *   (c) COPYRIGHT 2020 INFOR.  ALL RIGHTS RESERVED.           *
 *   THE WORD AND DESIGN MARKS SET FORTH HEREIN ARE            *
 *   TRADEMARKS AND/OR REGISTERED TRADEMARKS OF INFOR          *
 *   AND/OR ITS AFFILIATES AND SUBSIDIARIES. ALL RIGHTS        *
 *   RESERVED.  ALL OTHER TRADEMARKS LISTED HEREIN ARE         *
 *   THE PROPERTY OF THEIR RESPECTIVE OWNERS.                  *
 *                                                             *
 ***************************************************************
 */
 
 /*
 *Modification area - M3
 *Nbr               Date      User id          Description
 *SH001            20241001   ONKARK           script retrieves non conformity approval records from EXTOLN
 *
 *
 */
 

/****************************************************************************************
 Extension Name: EXT100MI/LstNonCoAppr
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date: 
 Description:
 * This script retrieves non-conformity approval records from the EXTOLN table for a given company.
 * It processes records where the STOP status is '2'(cancel) and the HSTP field equals '8'.
 * Return the list of lines.
 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created
******************************************************************************************/
public class LstNonCoAppr extends ExtendM3Transaction {
    private final MIAPI mi;
    private final LoggerAPI logger;
    private final DatabaseAPI database;
    private final MICallerAPI miCaller;

    private String inCONO;


    /*
     * @param mi - Infor MI Interface
     * @param logger - Infor Logging Interface
     * @param program - Infor Program Interface
     * @param database - Infor Database Interface
     * @param utility - Infor Utility Interface
     * @param miCaller - Infor MI Caller Interface
     */
    public LstNonCoAppr(MIAPI mi, LoggerAPI logger, DatabaseAPI database, MICallerAPI miCaller) {
        this.mi = mi;
        this.logger = logger;
        this.database = database;
        this.miCaller = miCaller;
    }

    public void main() {
        getAPIInput();        
        validateInput();      
        listOfrecords();        
    }


    /*
     * Retrieves the company number from the API input.
     */
    private void getAPIInput() {
        inCONO = mi.inData.get("CONO");  
    }

   
    /*
     * Validates the input parameters to ensure correctness.
     */
    private void validateInput() {
        checkValidCompany();  
    }

  
    /*
     * Validates the company number by checking its numeric value and existence in the CMNCMP table.
     */
    public void checkValidCompany() {
        // Check if the company number is numeric
       if (!inCONO =~ /^[0-9]+$/) {
            mi.error("Company number must only contain numeric values.");
            return;
        }

        // Query the CMNCMP table to validate the company
        DBAction query = database.table("CMNCMP").index("00").build();
        DBContainer container = query.getContainer();
        container.set("JICONO", inCONO.toInteger());

        // If the company is not found, return an error
        if (!query.read(container)) {
            mi.error("Company with number ${inCONO} not found.");
            return;
        }
    }

    
    /*
     * Processes the EXTOLN table to find records where STOP=2 and HSTP=8 
     */
    private void listOfrecords() {
        
        DBAction action = database.table("EXTOLN").index("00").selectAllFields().build();
        DBContainer container = action.createContainer();
        container.set("EXCONO", inCONO.toInteger());  // Set company number in the container

        // Closure to handle the listed records
        Closure<?> listRecords = { DBContainer data -> 
            // Process only if the order meets the criteria
            if (inCONO.equals(data.get("EXCONO").toString().trim()) && data.get("EXSTOP").toString().trim().equals("2")) {
                String currentHStp = data.get("EXHSTP").toString().trim();
                
                // Only output if the EXHSTP field is equal to 8
                if (currentHStp.equals("8")) {
                    mi.outData.put("ORNO", data.get("EXORNO").toString().trim());
                    mi.outData.put("CONO", data.get("EXCONO").toString().trim());
                    mi.outData.put("PONR", data.get("EXPONR").toString().trim());
                    mi.outData.put("POSX", data.get("EXPOSX").toString().trim());
                    mi.write();  // Output the result
                }
            }
        };

        // Determine the number of records to process, with a limit of 10,000
        // int nrOfRecords = 10000;
     int nrOfRecords = mi.getMaxRecords() <= 0 || mi.getMaxRecords() >= 10000? 10000: mi.getMaxRecords();    
        action.readAll(container, 1, nrOfRecords, listRecords);
    }
}
