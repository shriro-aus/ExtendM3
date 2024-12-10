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

/*************************************************************************************
Extension Name: EXT100MI/UpdCoStopAppr
Type: ExtendM3Transaction
Script Author: Onkar Kulkarni
Date:
Description
 * This script updates the stop status in the OOLINE table for a given order line.
 * It validates the input data for company, order number, and line number.
 * The STOP field is set based on the input value.

Revision History:
Name                    Date             Version          Description of Changes
Onkar Kulkarni       2024-10-01           1.0              Initial version created
 *************************************************************************************/
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

public class UpdCoStopAppr extends ExtendM3Transaction {

    private final MIAPI mi
    private final LoggerAPI logger
    private final DatabaseAPI database

    /**
     *
     * @param mi       MIAPI instance
     * @param logger   LoggerAPI instance
     * @param database DatabaseAPI instance
     */
    public UpdCoStopAppr(MIAPI mi, LoggerAPI logger, DatabaseAPI database) {
        this.mi = mi
        this.logger = logger
        this.database = database
    }

    // Variables for input fields
    private String inORNO, inPONR, inCONO, inPOSX, inSTOP ,inCHID
    private boolean isValidInput = true
    public int oolineChangeNo = -1

    // Get the current date and time
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern('yyyyMMdd')
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern('HHmmss')
    int currentDate = dateFormatter.format(LocalDate.now()).toInteger()
    int currentTime = timeFormatter.format(LocalTime.now()).toInteger()

    public void main() {
        getApiInput()

        if (validateApiInput()) {
            updateCoStopInOrderLine()
        }
    }

    /**
     * Method to fetch input data from API.
     */
    private void getApiInput() {
        inCONO = mi.inData.get('CONO') //(3) company
        inORNO = mi.inData.get('ORNO').trim() //(10) order number
        inPONR = mi.inData.get('PONR') //(5) PO line number
        inPOSX = mi.inData.get('POSX') == null || mi.inData.get('POSX').trim().isEmpty() || mi.inData.get('POSX').trim().equals('?') ? '0' : mi.inData.get('POSX').trim() // line suffix
        inSTOP = mi.inData.get('STOP').trim() // Stop value
    }

    /**
     * Method to validate the input fields.
     */
    private boolean validateApiInput() {
        isValidInput = checkValidCompany()

        if (isValidInput == false) {
            return false
        }
        isValidInput = checkValidOrderNumber()

        if (isValidInput == false) {
            return false
        }

        isValidInput = checkValidLineNumber()

        if (isValidInput == false) {
            return false
        }

        return true
    }

    /**
     * Method to update the CoStop field in the OOLINE table.
     */
    private void updateCoStopInOrderLine() {
        DBAction query = database.table('OOLINE')
            .index('00')
            .build()
        DBContainer container = query.getContainer()

        // Set parameters for the query
        container.set('OBORNO', inORNO)
        container.setInt('OBPONR', inPONR.toInteger())
        container.set('OBCONO', inCONO.toInteger())
        container.set('OBPOSX', Integer.parseInt(inPOSX))

        // Read the container and lock the record for updating
        if (query.read(container)) {
            query.readLock(container, updateCoStopCallBack)
        } else {
            // Error message if the record does not exist
            mi.error('Update failed: Record not found')
            return
        }
    }

    /**
     * Callback for updating the OOLINE table.
     */
    Closure<?> updateCoStopCallBack = { LockedResult lockedResult ->
        oolineChangeNo = lockedResult.get('OBCHNO')
        oolineChangeNo = oolineChangeNo  + 1
        inCHID = lockedResult.get('OBCHID')
        // Set OBOLSC based on the stop value
        lockedResult.set('OBOLSC', inSTOP.equals('1') ? 1 : 0)
        lockedResult.set('OBLMDT', currentDate)
        lockedResult.set('OBCHNO', oolineChangeNo)
        lockedResult.set('OBCHID', inCHID)

        lockedResult.update()
    }

    /**
     * Validate company existence.
     */
    public boolean checkValidCompany() {
        // Check if company code contains only numeric values
        if (!inCONO =~ /^[0-9]+$/) {
            mi.error("Invalid Company Code: '${inCONO}'. Company code should only contain numeric values.")
            return false
        }

        DBAction query = database.table('CMNCMP')
            .index('00')
            .build()
        DBContainer container = query.getContainer()
        container.set('JICONO', inCONO.toInteger())

        // Check if the company exists in the database
        if (!query.read(container)) {
            mi.error("Company Code '${inCONO}' not found. Please verify the company code.")
            return false
        }
        return true
    }

    /**
     * Validate order number.
     */
    public boolean checkValidOrderNumber() {
        // Check if the order number length is at least 10 characters
        if (inORNO.toString().length() < 10) {
            mi.error("Invalid Order Number: '${inORNO}'. Order number must be at least 10 characters long.")
            return false
        }

        DBAction query = database.table('OOHEAD')
            .index('00')
            .build()
        DBContainer container = query.getContainer()
        container.set('OACONO', inCONO.toInteger())
        container.set('OAORNO', inORNO)

        // Check if the order number exists in the database
        if (!query.read(container)) {
            mi.error("Order Number '${inORNO}' not found for Company Code '${inCONO}'. Please verify the order number.")
            return false
        }
        return true
    }

   /*
     * Validates PO line number input
     */
    public boolean checkValidLineNumber() {
        if (!inPONR =~ /^[0-9]+$/) {
            isValidInput = false
            mi.error('Invalid Line Number: Line number should contain only numeric values.')
            return false
        }

        DBAction query = database.table('OOLINE').index('00').build()
        DBContainer container = query.getContainer()
        container.set('OBCONO', inCONO.toInteger())
        container.set('OBORNO', inORNO)
        container.set('OBPONR', inPONR.toInteger())

        if (!inPOSX.equals('0')) {
            container.set('OBPOSX', inPOSX.toInteger())
        }

        if (!query.read(container)) {
            mi.error("Line Not Found: The line number ${inPONR} does not exist in order ${inORNO} for company ${inCONO}.")
            return false
        }

        return true
    }

}
