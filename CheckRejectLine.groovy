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

/****************************************************************************************
 Extension Name: EXT100MI/CheckRejectLine
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date:
 Description:
 * This script checks and processes rejected workflow lines in the EXTOLN table.
   It validates the input company, order number, and workflow status,
   and counts the number of rejected and In-Process lines matching the input criteria.

 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created

******************************************************************************************/
public class CheckRejectLine extends ExtendM3Transaction {

    private final MIAPI mi
    private final ProgramAPI program
    private final DatabaseAPI database
    private final LoggerAPI logger

    /*
     * Transaction EXT100MI/CheckRejectLine Interface
     * @param mi - Infor MI Interface
     * @param program - Infor Program Interface
     * @param database - Infor Database Interface
     * @param logger - Infor Logging Interface
     */
    public CheckRejectLine(MIAPI mi, ProgramAPI program, DatabaseAPI database, LoggerAPI logger) {
        this.mi = mi
        this.program = program
        this.database = database
        this.logger = logger
    }

    private String inORNO, inPONR, inWSTA, inCONO
    private int count = 0

    public void main() {
        getApiInput()                 // Retrieve input from API
        validateInput()               // Validate input values
        countRejecedAndProcessLines() // Count rejected lines and process
    }

    /*
     * Retrieves input parameters from the API.
     */
    private void getApiInput() {
        inCONO = mi.inData.get('CONO')  // Company
        inORNO = mi.inData.get('ORNO').trim()  // Order number
        inWSTA = mi.inData.get('WSTA').trim()  // Workflow status
    }

    /*
     * Validates the input received from the API.
     */
    private void validateInput() {
        checkValidCompany()
        checkValidOrderNumber()

        // Validate workflow status - should be either 10, 20, 30 ,40 ,50
        if (!inWSTA.equals('')) {
            if (!(inWSTA.equals('10') || inWSTA.equals('20') || inWSTA.equals('30') || inWSTA.equals('40') || inWSTA.equals('50'))) {
                mi.error('Invalid Workflow Status: Should be 10 (InProcess), 20 (Approved), 20 (Rejected), 40 (Copied), 50 (cancelled).')
                return
            }
        }
    }

    /*
     * Counts rejected lines and processes lines.
     */
    void countRejecedAndProcessLines() {
        DBAction query = database.table('EXTOLN').index('00').selection('EXCONO', 'EXORNO', 'EXWSTA').build()
        DBContainer container = query.getContainer()
        container.set('EXCONO', inCONO.toInteger())  // Set company number
        container.set('EXORNO', inORNO)         // Set order number
        container.set('EXWSTA', inWSTA)         // Set workflow status

        int nrOfKeys = 3
        int nrOfRecords = mi.getMaxRecords() <= 0 || mi.getMaxRecords() >= 10000 ? 10000 : mi.getMaxRecords()
        query.readAll(container, nrOfKeys, nrOfRecords, callback) // Read all matching records
        mi.outData.put('COUT', count.toString().trim()) // Output the count
        mi.write()       // Write output back
        count = 0        // Reset the count for next transaction
    }

    /*
     * Callback function to process each retrieved record.
     */
    Closure<?> callback = { DBContainer data ->
        if (inCONO.equals(data.get('EXCONO').toString().trim()) &&
            (inWSTA.equals(data.get('EXWSTA').toString().trim()) || data.get('EXWSTA').toString().trim().equals('10')) &&
            inORNO.equals(data.get('EXORNO').toString().trim())) {
            count++ // Increment count for matching records
            }
    }

    /*
     * Validates the company input value.
     */
    public void checkValidCompany() {
        // Ensure company code contains only numeric values
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error("Invalid Company Code: The company code ${inCONO} must only contain numeric values.")
            return
        }

        DBAction query = database.table('CMNCMP').index('00').build()
        DBContainer container = query.getContainer()
        container.set('JICONO', inCONO.toInteger())

        // Check if company exists
        if (!query.read(container)) {
            mi.error("Company Not Found: The company code ${inCONO} does not exist.")
            return
        }
    }

    /*
     * Validates the order number input value.
     */
    public void checkValidOrderNumber() {
        // Validate order number length
        if (inORNO.toString().length() < 10) {
            mi.error("Invalid Order Number: The order number ${inORNO} is too short. It must be at least 10 characters.")
            return
        }

        DBAction query = database.table('OOHEAD').index('00').build()
        DBContainer container = query.getContainer()
        container.set('OACONO', inCONO.toInteger())
        container.set('OAORNO', inORNO)

        // Check if order number exists
        if (!query.read(container)) {
            mi.error("Order Number Not Found: The order number ${inORNO} does not exist in company ${inCONO}.")
            return
        }
    }

}
