<%@ page language="java" %>
<%@ page import="org.metawidget.example.shared.addressbook.model.*, org.metawidget.example.shared.addressbook.controller.*, org.springframework.web.context.support.*" %>

<%@ taglib tagdir="/WEB-INF/tags" prefix="tags"%>
<%@ taglib uri="http://metawidget.org/html" prefix="mh"%>
<%@ taglib uri="http://metawidget.org/spring" prefix="m"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<%@ taglib uri="http://metawidget.org/example/spring/addressbook" prefix="a"%>

<tags:page>

	<c:choose>
		<c:when test="${contact['class'].simpleName == 'PersonalContact'}">
			<div id="page-image">
				<img src="media/personal.gif">
			</div>
			<div id="content">				
			<h1>Personal Contact</h1>
		</c:when>
		<c:otherwise>
			<div id="page-image">
				<img src="media/business.gif">
			</div>
			<div id="content">				
			<h1>Business Contact</h1>
		</c:otherwise>
	</c:choose>

		<form:form method="post" action="contact.html" modelAttribute="contact">

			<form:errors cssClass="errors"/>
			
			<form:errors path="firstname"/>
		
			<m:metawidget path="contact" readOnly="${readOnly}">

				<m:stub path="communications">
					<input type="hidden" name="deleteCommunicationId" id="deleteCommunicationId"/>
					<table class="data-table">
						<thead>
							<tr>
								<th class="column-half">Type</th>
								<th class="column-half">Value</th>
								<th class="column-tiny">&nbsp;</th>
							</tr>
						</thead>
						<tbody>
							<c:forEach items="${a:sortSet(contact.communications)}" var="_communication">
								<tr>
									<td class="column-half">${_communication.type}</td>
									<td class="column-half">${_communication.value}</td>
									<td class="column-tiny, table-buttons">
										<c:if test="${!readOnly}">
											<input type="submit" name="deleteCommunication" value="Delete" onClick="if ( !confirm( 'Are you sure you want to delete this communication?' )) return false; document.getElementById( 'deleteCommunicationId' ).value = '${_communication.id}'"/>
										</c:if>
									</td>
								</tr>
							</c:forEach>
						</tbody>
						<c:if test="${!readOnly}">
							<tfoot>
								<tr>						
									<jsp:useBean id="communication" class="org.metawidget.example.shared.addressbook.model.Communication"/>
									<td class="column-half"><mh:metawidget value="communication.type" style="width: 100%" /></td>
									<td class="column-half"><mh:metawidget value="communication.value" style="width: 100%" /></td>
									<td class="column-tiny, table-buttons"><input type="submit" name="addCommunication" value="Add"/></td>
								</tr>
							</foot>
						</c:if>
					</table>
				</m:stub>

				<m:facet name="footer">
					<c:choose>
						<c:when test="${readOnly}">
							<input type="submit" name="edit" value="<spring:message code="edit"/>"/>
							<input type="submit" name="cancel" value="<spring:message code="back"/>"/>
						</c:when>
						<c:otherwise>
							<input type="submit" name="save" value="<spring:message code="save"/>"/>
							<input type="submit" name="delete" value="<spring:message code="delete"/>" onclick="if ( !confirm( 'Sure you want to delete this contact?' )) return false"/>
							<input type="submit" name="cancel" value="<spring:message code="cancel"/>"/>
						</c:otherwise>
					</c:choose>
				</m:facet>

			</m:metawidget>

		</form:form>
	
	</div>

</tags:page>