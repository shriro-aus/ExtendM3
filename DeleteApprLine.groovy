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
 Extension Name: EXT300MI/DeleteApprLine
 Type: ExtendM3Transaction
 Script Author: Onkar Kulkarni
 Date:
 Description:
 * This script deletes a specific approval line from the EXTOLN table.
   It validates the company, order number, and line number,
   ensuring the record exists before performing the deletion.

 Revision History:
 Name                    Date             Version          Description of Changes
 Onkar Kulkarni       2024-10-01           1.0              Initial version created

******************************************************************************************/
public class DeleteApprLine extends ExtendM3Transaction {

    private final MIAPI mi
    private final LoggerAPI logger
    private final DatabaseAPI database

    /*
     * Transaction EXT300MI/DeleteApprLine Interface
     * @param mi - Infor MI Interface
     * @param logger - Infor Logging Interface
     * @param database - Infor Database Interface
     */
    public DeleteApprLine(MIAPI mi, LoggerAPI logger, DatabaseAPI database) {
        this.mi = mi
        this.logger = logger
        this.database = database
    }

    private String inORNO, inPONR, inDIVI, inWSTA, inCONO, inPOSX, inRGDT, inLMDT, inCHID, inRGTM, inCHNO

    public void main() {
        getAPIInput()  // Retrieve input from API
        validateInput() // Validate input values
        delete()        // Perform deletion
    }

    /*
     * Retrieves input parameters from the API.
     */
    private void getAPIInput() {
        inCONO = mi.inData.get('CONO')          // Company
        inORNO = mi.inData.get('ORNO').trim()   // Order number
        inPONR = mi.inData.get('PONR')          // PO line number
        inPOSX = (mi.inData.get('POSX') == null || mi.inData.get('POSX').trim().isEmpty()) ? '0' : mi.inData.get('POSX').trim() // Suffix number
    }

    /*
     * Validates the input received from the API.
     */
    private void validateInput() {
        checkValidCompany()
        checkValidOrderNumber()
        checkValidLineNumber()
    }

    /*
     * Deletes the approval line based on the input.
     */
    void delete() {
        DBAction query = database.table('EXTOLN').index('00').build()
        DBContainer container = query.getContainer()

        // Set container values
        container.set('EXCONO', inCONO.toInteger())  // Set company number
        container.set('EXORNO', inORNO)              // Set order number
        container.set('EXPONR', inPONR.toInteger())  // Set PO line number

        // Set POSX if not null or zero
        if (inPOSX != null && !inPOSX.equals('0')) {
            container.set('EXPOSX', Integer.parseInt(inPOSX)) // Set Suffix number
        }

        // Check if record exists
        if (query.read(container)) {
            query.readLock(container, callback) // Lock and delete record
        } else {
            mi.error("Record Not Found: The record for Order Number ${inORNO} does not exist in Company ${inCONO}.")
            return
        }
    }

    /*
     * Callback function to delete the record after reading.
     */
    Closure<?> callback = { LockedResult lockedResult ->
        lockedResult.delete() // Delete the locked record
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

    /*
     * Validates the line number input value.
     */
    public void checkValidLineNumber() {
        // Ensure line number contains only numeric values
        if (!inPONR =~ /^[0-9]+$/) {
            mi.error("Invalid Line Number: The line number ${inPONR} must only contain numeric values.")
            return
        }

        DBAction query = database.table('EXTOLN').index('00').build()
        DBContainer container = query.getContainer()
        container.set('EXCONO', inCONO.toInteger())
        container.set('EXORNO', inORNO)
        container.set('EXPONR', inPONR.toInteger())

        if (!inPOSX.equals('0')) {
            container.set('EXPOSX', inPOSX.toInteger())
        }

        // Check if line number exists
        if (!query.read(container)) {
            mi.error('Record does not exist')
            return
        }
    }

}
