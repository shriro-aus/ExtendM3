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
 *SH001            20241001   ONKARK           retrieves approval line info from EXTOLN
 *
 *
 */

/****************************************************************************************
 Extension Name: EXT100MI/GetApprovalLine
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date: 
 Description:
 * This script retrieves approval line information from the EXTOLN table. It validates the 
   company, order number, and line number, and outputs the corresponding approval line 
   details if the record exists.
    
 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created

******************************************************************************************/
public class GetApprovalLine extends ExtendM3Transaction {
    private final MIAPI mi;
    private final LoggerAPI logger;
    private final DatabaseAPI database;

    /*
     * Transaction EXT100MI/GetApprovalLine Interface
     * @param mi - Infor MI Interface
     * @param logger - Infor Logging Interface
     * @param database - Infor Database Interface
     */
    public GetApprovalLine(MIAPI mi, LoggerAPI logger, DatabaseAPI database) {
        this.mi = mi;
        this.logger = logger;
        this.database = database;
    }

    private String inORNO, inPONR, inDIVI, inWSTA, inCONO, inPOSX;

    
    public void main() {
        getApiInput();      // Retrieve input from API
        validateApiInput(); // Validate input values
        getLine();        // Fetch approval line details
    }

    /*
     * Retrieves input parameters from the API.
     */
    private void getApiInput() {
        inCONO = mi.inData.get("CONO");          // Company
        inORNO = mi.inData.get("ORNO").trim();   // Order number
        inPONR = mi.inData.get("PONR");          // PO line number
        inPOSX = (mi.inData.get("POSX") == null || mi.inData.get("POSX").trim().isEmpty()) ? "0" : mi.inData.get("POSX").trim(); // Line suffix
    }

    /*
     * Validates the input received from the API.
     */
    private void validateApiInput() {
        checkValidCompany();       
        checkValidOrderNumber();   
        checkValidLineNumber();   
    }

    /*
     * Retrieves line data from the EXTOLN table.
     */
    private void getLine() {
        DBAction action = database.table("EXTOLN").index("00").selectAllFields().build();
        DBContainer container = action.createContainer();

        // Set container values
        container.set("EXORNO", inORNO);
        container.setInt("EXPONR", inPONR.toInteger());
        container.set("EXPOSX", Integer.parseInt(inPOSX));
        container.set("EXCONO", inCONO.toInteger());

        // If record exists, retrieve fields and add to API output
        if (action.read(container)) {
            mi.outData.put("CONO", container.get("EXCONO").toString().trim());
            mi.outData.put("ORNO", container.get("EXORNO").toString().trim());
            mi.outData.put("PONR", container.get("EXPONR").toString().trim());
            mi.outData.put("POSX", container.get("EXPOSX").toString().trim());
            mi.outData.put("WSTA", container.get("EXWSTA").toString().trim());
            mi.outData.put("STOP", container.get("EXSTOP").toString().trim());
            mi.outData.put("TSID", container.get("EXTSID").toString().trim());
            mi.outData.put("HSTP", container.get("EXHSTP").toString().trim());
            mi.outData.put("LMDT", container.get("EXLMDT").toString().trim());
            mi.outData.put("RGDT", container.get("EXRGDT").toString().trim());
            mi.outData.put("RGTM", container.get("EXRGTM").toString().trim());
            mi.outData.put("CHNO", container.get("EXCHNO").toString().trim());
            mi.outData.put("CHID", container.get("EXCHID").toString().trim());
        } else {
            mi.error("Record Not Found: The record for Order Number ${inORNO} does not exist in Company ${inCONO}.");
            return;
        }
    }

    /*
     * Validates the company input value.
     */
    public void checkValidCompany() {
        // Ensure company code contains only numeric values
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error("Invalid Company Code: The company code ${inCONO} must only contain numeric values.");
            return;
        }

        DBAction query = database.table("CMNCMP").index("00").build();
        DBContainer container = query.getContainer();
        container.set("JICONO", inCONO.toInteger());

        // Check if company exists
        if (!query.read(container)) {
            mi.error("Company Not Found: The company code ${inCONO} does not exist.");
            return;
        }
    }

    /*
     * Validates the order number input value.
     */
    public void checkValidOrderNumber() {
        // Validate order number length
        if (inORNO.toString().length() < 10) {
            mi.error("Invalid Order Number: The order number ${inORNO} is too short. It must be at least 10 characters.");
            return;
        }

        DBAction query = database.table("OOHEAD").index("00").build();
        DBContainer container = query.getContainer();
        container.set("OACONO", inCONO.toInteger());
        container.set("OAORNO", inORNO);

        // Check if order number exists
        if (!query.read(container)) {
            mi.error("Order Number Not Found: The order number ${inORNO} does not exist in company ${inCONO}.");
            return;
        }
    }

    /*
     * Validates the line number input value.
     */
    public void checkValidLineNumber() {
        // Ensure line number contains only numeric values
        if (!inPONR =~ /^[0-9]+$/) {
            mi.error("Invalid Line Number: The line number ${inPONR} must only contain numeric values.");
            return;
        }

        DBAction query = database.table("EXTOLN").index("00").build();
        DBContainer container = query.getContainer();
        container.set("EXCONO", inCONO.toInteger());
        container.set("EXORNO", inORNO);
        container.set("EXPONR", inPONR.toInteger());

        if (!inPOSX.equals("0")) {
            container.set("EXPOSX", inPOSX.toInteger());
        }

        // Check if line number exists
        if (!query.read(container)) {
            mi.error("Line Number Not Found In EXTOLN table");
            return;
        }
    }
}
